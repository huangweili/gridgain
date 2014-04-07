/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.testsuites;

import junit.framework.*;
import org.gridgain.grid.logger.java.*;
import org.gridgain.grid.logger.jboss.*;
import org.gridgain.grid.logger.jcl.*;
import org.gridgain.grid.logger.log4j.*;

/**
 * Logging self-test suite.
 */
public class GridLoggingSelfTestSuite extends TestSuite {
    /**
    * @return P2P tests suite.
    */
   public static TestSuite suite() {
       TestSuite suite = new TestSuite("Gridgain Logging Test Suite");

       suite.addTest(new TestSuite(GridJavaLoggerTest.class));
       suite.addTest(new TestSuite(GridJBossLoggerTest.class));
       suite.addTest(new TestSuite(GridJclLoggerTest.class));
       suite.addTest(new TestSuite(GridLog4jInitializedTest.class));
       suite.addTest(new TestSuite(GridLog4jNotInitializedTest.class));
       suite.addTest(new TestSuite(GridLog4jCorrectFileNameTest.class));

       return suite;
   }
}
