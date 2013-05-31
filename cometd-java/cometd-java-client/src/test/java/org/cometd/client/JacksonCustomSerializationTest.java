/*
 * Copyright (c) 2012 the original author or authors.
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

package org.cometd.client;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSession;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.bayeux.server.LocalSession;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;
import org.cometd.common.JSONContext;
import org.cometd.common.JacksonJSONContextClient;
import org.cometd.server.BayeuxServerImpl;
import org.cometd.server.JacksonJSONContextServer;
import org.cometd.server.transport.HttpTransport;
import org.junit.Assert;
import org.junit.Test;

public class JacksonCustomSerializationTest extends ClientServerTest
{
    @Test
    public void testJacksonCustomSerialization() throws Exception
    {
        Map<String, String> serverOptions = new HashMap<String, String>();
        serverOptions.put(BayeuxServerImpl.JSON_CONTEXT, TestJacksonJSONContextServer.class.getName());
        serverOptions.put(HttpTransport.JSON_DEBUG_OPTION, "true");
        Map<String, Object> clientOptions = new HashMap<String, Object>();
        clientOptions.put(ClientTransport.JSON_CONTEXT, new TestJacksonJSONContextClient());

        startServer(serverOptions);

        String channelName = "/data";
        final String content = "random";
        final CountDownLatch latch = new CountDownLatch(1);

        LocalSession service = bayeux.newLocalSession("custom_serialization");
        service.handshake();
        service.getChannel(channelName).subscribe(new ClientSessionChannel.MessageListener()
        {
            public void onMessage(ClientSessionChannel channel, Message message)
            {
                Data data = (Data)message.getData();
                Assert.assertEquals(content, data.content);
                Map<String, Object> ext = message.getExt();
                Assert.assertNotNull(ext);
                Extra extra = (Extra)ext.get("extra");
                Assert.assertEquals(content, extra.content);
                latch.countDown();
            }
        });

        BayeuxClient client = new BayeuxClient(cometdURL, new LongPollingTransport(clientOptions, httpClient));
        client.setDebugEnabled(debugTests());
        client.addExtension(new ExtraExtension(content));

        client.handshake();
        Assert.assertTrue(client.waitFor(5000, BayeuxClient.State.CONNECTED));
        // Wait for the connect to establish
        Thread.sleep(1000);

        client.getChannel(channelName).publish(new Data(content));
        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));

        disconnectBayeuxClient(client);
    }

    @Test
    public void testParserGenerator() throws Exception
    {
        // Note: Jackson does not seem to be able to serialize/deserialize correctly a single Data/Extra object.
        // However, if they are put into a container like a Map, then Jackson produces a different JSON than
        // what it produces for the standalone object that allows correct deserialization, of this form:
        // { field: ["className", {object}] }
        // It is way easier to have Jetty serialize and deserialize this form than make Jackson use Jetty's form.
        // They problem is that Jackson tries to be "smart" in figuring out the typing, but with a Map<String, Object>
        // there is no way to have type information for the values, so Jackson defaults to a basic deserializer
        // that either is not very flexible, or it's very difficult to configure, so much that I could not so far.

        JSONContext.Client jsonContext = new TestJacksonJSONContextClient();
        Data data1 = new Data("data");
        Extra extra1 = new Extra("extra");
        Map<String, Object> map1 = new HashMap<String, Object>();
        map1.put("data", data1);
        map1.put("extra", extra1);
        String json = jsonContext.getGenerator().generate(map1);
        Map map2 = jsonContext.getParser().parse(new StringReader(json), Map.class);
        Data data2 = (Data)map2.get("data");
        Extra extra2 = (Extra)map2.get("extra");
        Assert.assertEquals(data1.content, data2.content);
        Assert.assertEquals(extra1.content, extra2.content);
    }

    private static class ExtraExtension extends ClientSession.Extension.Adapter
    {
        private final String content;

        public ExtraExtension(String content)
        {
            this.content = content;
        }

        @Override
        public boolean send(ClientSession session, Message.Mutable message)
        {
            Map<String, Object> ext = message.getExt(true);
            ext.put("extra", new Extra(content));
            return true;
        }
    }

    public static class TestJacksonJSONContextServer extends JacksonJSONContextServer
    {
        public TestJacksonJSONContextServer()
        {
            getObjectMapper().enableDefaultTyping(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
        }
    }

    public static class TestJacksonJSONContextClient extends JacksonJSONContextClient
    {
        public TestJacksonJSONContextClient()
        {
            getObjectMapper().enableDefaultTyping(ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT);
        }
    }

    private static class Data
    {
        @JsonProperty
        private String content;

        private Data()
        {
            // Needed by Jackson
        }

        private Data(String content)
        {
            this.content = content;
        }
    }

    private static class Extra
    {
        @JsonProperty
        private String content;

        private Extra()
        {
            // Needed by Jackson
        }

        private Extra(String content)
        {
            this.content = content;
        }
    }
}
