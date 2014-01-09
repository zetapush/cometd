/*
 * Copyright (c) 2008-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cometd.javascript;

import org.junit.Assert;
import org.junit.Test;

public class CometDTransportNegotiationFailureTest extends AbstractCometDTest
{
    @Test
    public void testTransportNegotiationFailureForClientLongPollingServerWebSocket() throws Exception
    {
        // Only websocket on server, only long-polling on client
        bayeuxServer.setAllowedTransports("websocket");
        evaluateScript("keep_only_long_polling_transport",
                "cometd.unregisterTransports();" +
                        "cometd.registerTransport('long-polling', originalTransports['long-polling']);");

        defineClass(Latch.class);
        evaluateScript("var failureLatch = new Latch(1);");
        Latch failureLatch = get("failureLatch");
        evaluateScript("cometd.onTransportFailure = function(oldTransport, newTransport, failure)" +
                "{" +
                "    failureLatch.countDown();" +
                "}");
        evaluateScript("cometd.init({url: '" + cometdURL + "', logLevel: '" + getLogLevel() + "'});");

        Assert.assertTrue(failureLatch.await(5000));

        evaluateScript("cometd.disconnect(true);");
    }

    @Test
    public void testTransportNegotiationFailureForClientWebSocketServerLongPolling() throws Exception
    {
        // Only long-polling on server, only websocket on client
        bayeuxServer.setAllowedTransports("long-polling");
        evaluateScript("keep_only_websocket_transport",
                "cometd.unregisterTransports();" +
                "cometd.registerTransport('websocket', originalTransports['websocket']);");

        defineClass(Latch.class);
        evaluateScript("var failureLatch = new Latch(1);");
        Latch failureLatch = get("failureLatch");
        evaluateScript("cometd.onTransportFailure = function(oldTransport, newTransport, failure)" +
                "{" +
                "    failureLatch.countDown();" +
                "}");
        evaluateScript("cometd.init({url: '" + cometdURL + "', logLevel: '" + getLogLevel() + "'});");

        Assert.assertTrue(failureLatch.await(5000));
        Assert.assertTrue((Boolean)evaluateScript("cometd.isDisconnected();"));
    }
}
