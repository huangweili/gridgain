/* 
 Copyright (C) GridGain Systems. All Rights Reserved.
 
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0
 
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal.websession;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.servlet.*;
import org.eclipse.jetty.webapp.*;
import org.gridgain.grid.*;
import org.gridgain.grid.cache.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.gridgain.testframework.*;
import org.gridgain.testframework.junits.common.*;
import org.jetbrains.annotations.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Tests the correctness of web sessions caching functionality.
 */
public class GridWebSessionSelfTest extends GridCommonAbstractTest {
    /** Port for test Jetty server. */
    private static final int TEST_JETTY_PORT = 49090;

    /** Servers count in load test. */
    private static final int SRV_CNT = 3;

    /**
     * @return Name of the cache for this test.
     */
    protected String getCacheName() {
        return "partitioned";
    }

    /**
     * @throws Exception If failed.
     */
    public void testSingleRequest() throws Exception {
        testSingleRequest("/examples/config/example-cache.xml");
    }

    /**
     * @throws Exception If failed.
     */
    public void testSingleRequestMetaInf() throws Exception {
        testSingleRequest("gg-config.xml");
    }

    /**
     * Tests single request to a server. Checks the presence of session in cache.
     *
     * @param cfg Configuration.
     * @throws Exception If failed.
     */
    private void testSingleRequest(String cfg) throws Exception {
        Server srv = null;

        try {
            srv = startServer(TEST_JETTY_PORT, cfg, null, new SessionCreateServlet());

            URLConnection conn = new URL("http://localhost:" + TEST_JETTY_PORT + "/ggtest/test").openConnection();

            conn.connect();

            try (BufferedReader rdr = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String sesId = rdr.readLine();

                GridCache<String, HttpSession> cache = G.grid().cache(getCacheName());

                assertNotNull(cache);

                HttpSession ses = cache.get(sesId);

                assertNotNull(ses);
                assertEquals("val1", ses.getAttribute("key1"));
            }
        }
        finally {
            stopServer(srv);
        }
    }

    /**
     * @throws Exception If failed.
     */
    public void testRestarts() throws Exception {
        final AtomicReference<String> sesIdRef = new AtomicReference<>();

        final AtomicReferenceArray<Server> srvs = new AtomicReferenceArray<>(SRV_CNT);

        for (int idx = 0; idx < SRV_CNT; idx++) {
            String cfg = "/modules/core/src/test/config/websession/spring-cache-" + (idx + 1) + ".xml";

            srvs.set(idx, startServer(
                TEST_JETTY_PORT + idx, cfg, "grid-" + (idx + 1), new RestartsTestServlet(sesIdRef)));
        }

        final AtomicBoolean stop = new AtomicBoolean();

        GridFuture<?> restarterFut = GridTestUtils.runMultiThreadedAsync(new Callable<Object>() {
            @SuppressWarnings("BusyWait")
            @Override public Object call() throws Exception {
                Random rnd = new Random();

                for (int i = 0; i < 10; i++) {
                    int idx = -1;
                    Server srv = null;

                    while (srv == null) {
                        idx = rnd.nextInt(SRV_CNT);

                        srv = srvs.getAndSet(idx, null);
                    }

                    assert idx != -1;
                    assert srv != null;

                    stopServer(srv);

                    String cfg = "/modules/core/src/test/config/websession/spring-cache-" + (idx + 1) + ".xml";

                    srv = startServer(
                        TEST_JETTY_PORT + idx, cfg, "grid-" + (idx + 1), new RestartsTestServlet(sesIdRef));

                    assert srvs.compareAndSet(idx, null, srv);

                    Thread.sleep(100);
                }

                X.println("Stopping...");

                stop.set(true);

                return null;
            }
        }, 1, "restarter");

        Server srv = null;

        try {
            Random rnd = new Random();

            int n = 0;

            while (!stop.get()) {
                int idx = -1;

                while (srv == null) {
                    idx = rnd.nextInt(SRV_CNT);

                    srv = srvs.getAndSet(idx, null);
                }

                assert idx != -1;
                assert srv != null;

                int port = TEST_JETTY_PORT + idx;

                URLConnection conn = new URL("http://localhost:" + port + "/ggtest/test").openConnection();

                String sesId = sesIdRef.get();

                if (sesId != null)
                    conn.addRequestProperty("Cookie", "JSESSIONID=" + sesId);

                conn.connect();

                String str;

                try (BufferedReader rdr = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    str = rdr.readLine();
                }

                assertEquals(n, Integer.parseInt(str));

                n++;

                assert srvs.compareAndSet(idx, null, srv);

                srv = null;
            }

            X.println(">>> Made " + n + " requests.");
        }
        finally {
            restarterFut.get();

            if (srv != null)
                stopServer(srv);

            for (int i = 0; i < srvs.length(); i++)
                stopServer(srvs.get(i));
        }
    }

    /**
     * @param cfg Configuration.
     * @param gridName Grid name.
     * @param servlet Servlet.
     * @return Servlet container web context for this test.
     */
    protected WebAppContext getWebContext(@Nullable String cfg, @Nullable String gridName, HttpServlet servlet) {
        WebAppContext ctx = new WebAppContext(U.resolveGridGainPath("modules/core/src/test/webapp").getAbsolutePath(),
            "/ggtest");

        ctx.setInitParameter("GridGainConfigurationFilePath", cfg);
        ctx.setInitParameter("GridGainWebSessionsGridName", gridName);
        ctx.setInitParameter("GridGainWebSessionsCacheName", getCacheName());
        ctx.setInitParameter("GridGainWebSessionsMaximumRetriesOnFail", "100");

        ctx.addServlet(new ServletHolder(servlet), "/*");

        return ctx;
    }

    /**
     * Starts server.
     *
     * @param port Port number.
     * @param cfg Configuration.
     * @param gridName Grid name.
     * @param servlet Servlet.
     * @return Server.
     * @throws Exception In case of error.
     */
    private Server startServer(int port, @Nullable String cfg, @Nullable String gridName, HttpServlet servlet)
        throws Exception {
        Server srv = new Server(port);

        WebAppContext ctx = getWebContext(cfg, gridName, servlet);

        srv.setHandler(ctx);

        srv.start();

        return srv;
    }

    /**
     * Stops server.
     *
     * @param srv Server.
     * @throws Exception In case of error.
     */
    private void stopServer(@Nullable Server srv) throws Exception {
        if (srv != null)
            srv.stop();
    }

    /**
     * Test servlet.
     */
    private static class SessionCreateServlet extends HttpServlet {
        /** {@inheritDoc} */
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
            HttpSession ses = req.getSession(true);

            ses.setAttribute("checkCnt", 0);
            ses.setAttribute("key1", "val1");
            ses.setAttribute("key2", "val2");

            X.println(">>>", "Created session: " + ses.getId(), ">>>");

            res.getWriter().write(ses.getId());

            res.getWriter().flush();
        }
    }

    /**
     * Servlet for restarts test.
     */
    private static class RestartsTestServlet extends HttpServlet {
        /** Session ID. */
        private final AtomicReference<String> sesId;

        /**
         * @param sesId Session ID.
         */
        RestartsTestServlet(AtomicReference<String> sesId) {
            this.sesId = sesId;
        }

        /** {@inheritDoc} */
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
            HttpSession ses = req.getSession(true);

            sesId.compareAndSet(null, ses.getId());

            Integer attr = (Integer)ses.getAttribute("attr");

            if (attr == null)
                attr = 0;
            else
                attr++;

            ses.setAttribute("attr", attr);

            res.getWriter().write(attr.toString());

            res.getWriter().flush();
        }
    }
}
