// ========================================================================
// Copyright 2003-2005 Mort Bay Consulting Pty. Ltd.
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.mortbay.jetty.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.mortbay.io.Buffer;
import org.mortbay.io.Connection;
import org.mortbay.io.nio.SelectChannelEndPoint;
import org.mortbay.io.nio.SelectorManager;
import org.mortbay.io.nio.SelectorManager.SelectSet;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.RetryRequest;
import org.mortbay.log.Log;
import org.mortbay.thread.Timeout;
import org.mortbay.util.ajax.Continuation;

/* ------------------------------------------------------------------------------- */
/**
 * Selecting NIO connector.
 * <p>
 * This connector uses efficient NIO buffers with a non blocking threading model. Direct NIO buffers
 * are used and threads are only allocated to connections with requests. Synchronization is used to
 * simulate blocking for the servlet API, and any unflushed content at the end of request handling
 * is written asynchronously.
 * </p>
 * <p>
 * This connector is best used when there are a many connections that have idle periods.
 * </p>
 * <p>
 * When used with {@link org.mortbay.util.ajax.Continuation}, threadless waits are supported. When
 * a filter or servlet calls getEvent on a Continuation, a {@link org.mortbay.jetty.RetryRequest}
 * runtime exception is thrown to allow the thread to exit the current request handling. Jetty will
 * catch this exception and will not send a response to the client. Instead the thread is released
 * and the Continuation is placed on the timer queue. If the Continuation timeout expires, or it's
 * resume method is called, then the request is again allocated a thread and the request is retried.
 * The limitation of this approach is that request content is not available on the retried request,
 * thus if possible it should be read after the continuation or saved as a request attribute or as the
 * associated object of the Continuation instance.
 * </p>
 * 
 * @org.apache.xbean.XBean element="nioConnector" description="Creates an NIO based socket connector"
 * 
 * @author gregw
 *
 */
public class SelectChannelConnector extends AbstractNIOConnector 
{
    private transient ServerSocketChannel _acceptChannel;

    private SelectorManager _manager = new SelectorManager()
    {
        protected SocketChannel acceptChannel(SelectionKey key) throws IOException
        {
            SocketChannel channel = ((ServerSocketChannel)key.channel()).accept();
            if (channel==null)
                return null;
            channel.configureBlocking(false);
            Socket socket = channel.socket();
            configure(socket);
            return channel;
        }

        protected boolean dispatch(Runnable task) throws IOException
        {
            return getThreadPool().dispatch(task);
        }

        protected void endPointClosed(SelectChannelEndPoint endpoint)
        {
            connectionClosed((HttpConnection)endpoint.getConnection());
        }

        protected void endPointOpened(SelectChannelEndPoint endpoint)
        {
            connectionOpened((HttpConnection)endpoint.getConnection());
        }

        protected Connection newConnection(SocketChannel channel,SelectChannelEndPoint endpoint)
        {
            return new HttpConnection(SelectChannelConnector.this,endpoint,getServer());
        }

        protected SelectChannelEndPoint newEndPoint(SocketChannel channel, SelectSet selectSet, SelectionKey sKey) throws IOException
        {
            return SelectChannelConnector.this.newEndPoint(channel,selectSet,sKey);
        }
    };
    
    /* ------------------------------------------------------------------------------- */
    /**
     * Constructor.
     * 
     */
    public SelectChannelConnector()
    {
    }
    
    /* ------------------------------------------------------------ */
    public void accept(int acceptorID) throws IOException
    {
        _manager.doSelect(acceptorID);
    }
    
    /* ------------------------------------------------------------ */
    public void stopAccept(int acceptorID) throws Exception
    {
        _manager.doStop(acceptorID);
    }
    
    /* ------------------------------------------------------------ */
    public void close() throws IOException
    {
        if (_acceptChannel != null)
            _acceptChannel.close();
        _acceptChannel = null;

    }
    
    /* ------------------------------------------------------------------------------- */
    public void customize(org.mortbay.io.EndPoint endpoint, Request request) throws IOException
    {
        super.customize(endpoint, request);
    }

    /* ------------------------------------------------------------ */
    public Object getConnection()
    {
        return _acceptChannel;
    }
    
    /* ------------------------------------------------------------ */
    /** Assume Short Dispatch
     * If true, the select set is not updated when a endpoint is dispatched for
     * reading. The assumption is that the task will be short and thus will probably
     * be complete before the select is tried again.
     * @return Returns the assumeShortDispatch.
     */
    public boolean getDelaySelectKeyUpdate()
    {
        return _manager.isDelaySelectKeyUpdate();
    }

    /* ------------------------------------------------------------------------------- */
    public int getLocalPort()
    {
        if (_acceptChannel==null || !_acceptChannel.isOpen())
            return -1;
        return _acceptChannel.socket().getLocalPort();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.mortbay.jetty.Connector#newContinuation()
     */
    public Continuation newContinuation()
    {
        return new RetryContinuation();
    }

    /* ------------------------------------------------------------ */
    public void open() throws IOException
    {
        if (_acceptChannel == null)
        {
            // Create a new server socket and set to non blocking mode
            _acceptChannel = ServerSocketChannel.open();
            _acceptChannel.configureBlocking(false);

            // Bind the server socket to the local host and port
            InetSocketAddress addr = getHost()==null?new InetSocketAddress(getPort()):new InetSocketAddress(getHost(),getPort());
            _acceptChannel.socket().bind(addr,getAcceptQueueSize());

            // Register accepts on the server socket with the selector.
            _manager.register(_acceptChannel,SelectionKey.OP_ACCEPT);
        }
    }


    /* ------------------------------------------------------------ */
    /**
     * @param delay If true, updating a {@link SelectionKey} is delayed until a redundant event is 
     * schedules.  This is an optimization that assumes event handling can be completed before the next select
     * completes.
     */
    public void setDelaySelectKeyUpdate(boolean delay)
    {
        _manager.setDelaySelectKeyUpdate(delay);
    }

    /* ------------------------------------------------------------ */
    public void setMaxIdleTime(int maxIdleTime)
    {
        _manager.setMaxIdleTime(maxIdleTime);
        super.setMaxIdleTime(maxIdleTime);
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.mortbay.jetty.AbstractConnector#doStart()
     */
    protected void doStart() throws Exception
    {
        _manager.setSelectSets(getAcceptors());
        _manager.setMaxIdleTime(getMaxIdleTime());
        _manager.start();
        super.doStart();
    }

    /* ------------------------------------------------------------ */
    /*
     * @see org.mortbay.jetty.AbstractConnector#doStop()
     */
    protected void doStop() throws Exception
    {
        _manager.stop();
        super.doStop();
    }

    /* ------------------------------------------------------------ */
    protected SelectChannelEndPoint newEndPoint(SocketChannel channel, SelectSet selectSet, SelectionKey key) throws IOException
    {
        return new ConnectorEndPoint(channel,selectSet,key);
    }
    

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public static class ConnectorEndPoint extends SelectChannelEndPoint
    {
        public ConnectorEndPoint(SocketChannel channel, SelectSet selectSet, SelectionKey key)
        {
            super(channel,selectSet,key);
            scheduleIdle();
        }

        public void close() throws IOException
        {
            RetryContinuation continuation = (RetryContinuation) ((HttpConnection)getConnection()).getRequest().getContinuation();
            if (continuation != null && continuation.isPending())
                continuation.reset();

            super.close();
        }
        
        public boolean dispatch(boolean assumeShortDispatch) throws IOException
        {
            cancelIdle();
            return super.dispatch(assumeShortDispatch);
        }

        public void undispatch()
        {
            RetryContinuation continuation = (RetryContinuation) ((HttpConnection)getConnection()).getRequest().getContinuation();

            if (continuation != null)
            {
                // We have a continuation
                Log.debug("continuation {}", continuation);
                if (!continuation.schedule())
                    super.undispatch();
            }
            else
            {
                super.undispatch();
                if (_connection.isIdle())
                    scheduleIdle();
            }
            
        }

    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public static class RetryContinuation extends Timeout.Task implements Continuation, Runnable
    {
        SelectChannelEndPoint _endPoint=(SelectChannelEndPoint)HttpConnection.getCurrentConnection().getEndPoint();
        boolean _new = true;
        Object _object;
        boolean _pending = false;
        boolean _resumed = false;
        RetryRequest _retry;
        boolean _scheduled =false;
        long _timeout;
        
        public void expire()
        {
            boolean redispatch=false;
            synchronized (this)
            {
                redispatch=_scheduled && _pending && !_resumed;
            }
            if (redispatch)
            {
                _endPoint.getSelectSet().addChange(this);
                _endPoint.getSelectSet().wakeup();
            }
        }
        
        public Object getObject()
        {
            return _object;
        }

        public long getTimeout()
        {
            return _timeout;
        }

        public boolean isNew()
        {
            return _new;
        }

        public boolean isPending()
        {
            return _pending;
        }

        public void reset()
        {
            synchronized (this)
            {
                _resumed = false;
                _pending = false;
                _scheduled = false;
            }
            
            synchronized (_endPoint.getSelectSet())
            {
                this.cancel();   
            }
        } 
        
        public void resume()
        {
            boolean redispatch=false;
            synchronized (this)
            {
                if (_pending && !isExpired())
                {
                    _resumed = true;
                    redispatch=_scheduled;
                }
            }

            if (redispatch)
            {
                SelectSet selectSet = _endPoint.getSelectSet();
                
                synchronized (selectSet)
                {
                    this.cancel();   
                }

                selectSet.addChange(this);
                selectSet.wakeup();
            }
        }
        
        public void run()
        {
            _endPoint.run();
        }
        
        /* Called when a run exits */
        public boolean schedule()
        {
            boolean redispatch=false;
        
            synchronized (this)
            {
                if (!_pending)
                    return false;
                _scheduled = true;
                
                redispatch=isExpired() || _resumed;
            }
            
            if (redispatch)
                _endPoint.getSelectSet().addChange(this);
            
            else
                _endPoint.getSelectSet().scheduleTimeout(this,_timeout);
            
            _endPoint.getSelectSet().wakeup();
            return true;
        }

        public void setObject(Object object)
        {
            _object = object;
        }

        public boolean suspend(long timeout)
        {
            boolean resumed=false;
            synchronized (this)
            {
                resumed=_resumed;
                _resumed=false;
                _new = false;
                if (!_pending && !resumed && timeout >= 0)
                {
                    _pending=true;
                    _scheduled = false;
                    _timeout = timeout;
                    if (_retry==null)
                     _retry = new RetryRequest();
                    throw _retry;
                }
                
                // here only if suspend called on pending continuation.
                // acts like a reset
                _resumed = false;
                _pending = false;
            }

            synchronized (_endPoint.getSelectSet())
            {
                this.cancel();   
            }

            return resumed;
        }
    }

}
