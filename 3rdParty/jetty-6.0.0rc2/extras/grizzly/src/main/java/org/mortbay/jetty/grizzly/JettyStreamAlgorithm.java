//========================================================================
// Parts Copyright 2006 Mort Bay Consulting Pty. Ltd.
// Parts Copyright 2006 Jeanfrancois Arcand
//------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//========================================================================

package org.mortbay.jetty.grizzly;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import com.sun.enterprise.web.connector.grizzly.Handler;
import com.sun.enterprise.web.connector.grizzly.SelectorThread;
import com.sun.enterprise.web.connector.grizzly.algorithms.NoParsingAlgorithm;
import com.sun.enterprise.web.connector.grizzly.algorithms.StreamAlgorithmBase;

/**
 * @author gregw
 *
 */
public class JettyStreamAlgorithm extends NoParsingAlgorithm
{
    public boolean parse(ByteBuffer byteBuffer)
    {
        System.err.println(this+" parse(ByteBuffer)");
        // TODO Auto-generated method stub
        return super.parse(byteBuffer);
    }

    public Handler getHandler()
    {
        System.err.println(this+" getHandler()");
        // TODO Auto-generated method stub
        return super.getHandler();
    }

    public Class getReadTask(SelectorThread selectorThread)
    {
        System.err.println(this+" getReadTask()");
        return JettyReadTask.class;
    }

    public void setPort(int port)
    {
        System.err.println(this+" setPort("+port+")");
        super.setPort(port);
    }

    public void setSocketChannel(SocketChannel socketChannel)
    {
        System.err.println(this+" setSocketChannel("+socketChannel+")");
        super.setSocketChannel(socketChannel);
    }

}
