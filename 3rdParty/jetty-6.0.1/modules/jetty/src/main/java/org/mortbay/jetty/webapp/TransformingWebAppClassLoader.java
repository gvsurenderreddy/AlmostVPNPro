package org.mortbay.jetty.webapp;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;

import org.mortbay.io.tx.Handler;
import org.mortbay.io.tx.Transformer;
import org.mortbay.log.Log;
import org.mortbay.resource.JarResource;
import org.mortbay.resource.Resource;
import org.mortbay.util.LazyList;

/** Transforming Web Application ClassLoader.
 * 
 * This extension to {@link org.mortbay.jetty.webapp.WebAppClassLoader} provides the ability for 
 * the bytes of the classes to be transformed as they are loaded.   This is achieved by installing the
 * {@link org.mortbay.io.tx.Handler} instance of {@link java.net.URLStreamHandler} for the psuedo protocol
 * "tx".  This allows loading by a URLClassLoader to be intercepted, but it also requests that this class
 * extract all jar files to a temporary directory.
 * <p/>
 * When a resource is loaded, the {@link #transform(URL, byte[])} method is called, which should be
 * extended by a derived class to implement the required transformations.
 *  
 * @author gregw
 *
 */
public class TransformingWebAppClassLoader extends WebAppClassLoader implements Transformer
{
    static
    {
        Log.info("Register org.mortbay.io.tx  URLStreamHandler");
        Handler.register();
    }
    
    public TransformingWebAppClassLoader(WebAppContext context)
    {
        super(null, context);
    }
    
    public TransformingWebAppClassLoader(ClassLoader parent, WebAppContext context)
    {
        super(parent, context);
    }
    
    public void addURL(URL url)
    {
        try
        {
            Resource resource=Resource.newResource(url);
            if(!resource.isDirectory()&& resource.exists())
            {
                String jar=url.getFile();
                int slash=jar.lastIndexOf('/');
                if (slash>=0)
                    jar=jar.substring(slash+1);
                
                File tmp=File.createTempFile(jar+"-",null,getContext().getTempDirectory());
                tmp.delete();
                tmp.mkdir();
                resource=Resource.newResource("jar:"+resource+"!/");
                Log.info("extract "+url+" to "+tmp);
                JarResource.extract(resource,tmp,true);
                url=tmp.toURL();
            }
            
            url=new URL("tx:"+url);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        Log.info("transforming "+url);
        
        super.addURL(url);
    }
    
    public byte[] transform(URL src, byte[] content)
    {
        return content;
    }

    public URL getResource(String name)
    {
        URL url = super.getResource(name);
        if (url!=null && url.getProtocol().equals(Handler.PROTOCOL))
        {
            try
            {
                url=new URL(url.getFile());
            }
            catch (MalformedURLException e)
            {
                Log.warn(e);
            }
        }
        return url;
    }

    public URL findResource(String name)
    {
        URL url = super.findResource(name);
        if (url!=null && url.getProtocol().equals(Handler.PROTOCOL))
        {
            try
            {
                url=new URL(url.getFile());
            }
            catch (MalformedURLException e)
            {
                Log.warn(e);
            }
        }
        return url;
    }

    public Enumeration findResources(String name) throws IOException
    {
        Enumeration en=super.findResources(name);
        Object list = null;
        while(en!=null && en.hasMoreElements())
        {
            URL url = (URL)en.nextElement();
            if (url!=null && url.getProtocol().equals(Handler.PROTOCOL))
            {
                try
                {
                    url=new URL(url.getFile());
                }
                catch (MalformedURLException e)
                {
                    Log.warn(e);
                }
            }
            list=LazyList.add(list,url);
        }
        
        return Collections.enumeration(LazyList.getList(list));
    }
}
