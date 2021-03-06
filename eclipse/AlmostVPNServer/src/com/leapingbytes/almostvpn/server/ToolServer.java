package com.leapingbytes.almostvpn.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.leapingbytes.almostvpn.model.AVPNConfiguration;
import com.leapingbytes.almostvpn.model.Model;
import com.leapingbytes.almostvpn.path.PathLocator;
import com.leapingbytes.almostvpn.server.jetty.JettyServer;
import com.leapingbytes.almostvpn.server.profile.Profile;
import com.leapingbytes.almostvpn.server.profile.ProfileException;
import com.leapingbytes.almostvpn.server.profile.configurator.impl.SessionConfigurator;
import com.leapingbytes.almostvpn.server.profile.item.impl.SSHSession;
import com.leapingbytes.almostvpn.socks.DynamicProxy;
import com.leapingbytes.almostvpn.util.DataCopier;
import com.leapingbytes.almostvpn.util.Env;
import com.leapingbytes.almostvpn.util.SecureStorageProvider;
import com.leapingbytes.jcocoa.NSDictionary;

public class ToolServer {
	static {
		Env.init();
	}
	
	private static Log log = LogFactory.getLog( ToolServer.class );
	
	public static final String CONFIGURATION_COMMAND	= "+configuration";

	public static final String START_PROFILE_COMMAND 	= "+start";
	public static final String STOP_PROFILE_COMMAND 	= "+stop";
	
	public static final String STATUS_COMMAND 			= "+status";
	
	public static final String EXIT_COMMAND 			= "+exit";
	
	public static final String STARTED_MARKER			= "-STARTED-";
	
	public static final int DEFAULT_CONTROL_SERVER_PORT = Integer.getInteger( "com.leapingbytes.almostvpn.tool.server.Port", 1313 ).intValue();
	
	public static int	_controlServerPort 			= 
		Integer.getInteger( PathLocator.PRODUCT_ID + ".serverPort", DEFAULT_CONTROL_SERVER_PORT ).intValue();
	
	JettyServer			_server;
	
	static PathLocator				_pathLocator 			= PathLocator.sharedInstance();
	static String 					_preferencesFilePath 	= _pathLocator.preferencesFilePath();
	static long						_preferencesFileTimestamp = 0;
	static AVPNConfiguration		_configuration			= new AVPNConfiguration();
	static ToolServer				_toolServer				= null;
	static boolean					_debugMode				= false;
	static boolean					_shellMode				= false;
	static int						_shellPort				= 0;
	static String					_shellDev				= null;
	
	static DynamicProxy				_dynamicProxy			= null;

	private static String 			_version 				= "n/a";	
	
	public static String			version() {
		return _version;
	}
		
	public static void main( String[] args ) {
		try {
			System.setProperty( "socksNonProxyHosts", "*");
	
			Logger.getLogger( ToolServer.class.getName() ).setLevel( Level.ALL );
	
			InputStream is = ClassLoader.getSystemClassLoader().getResourceAsStream( "com/leapingbytes/almostvpn/util/timestamp.txt" );
			if( is != null ) {
				byte[] buffer = new byte[ 1024 ];
				int rc;
				try {
					rc = is.read( buffer );
					_version = new String( buffer, 0, rc );
				} catch (IOException e) {
					// DO NOTING
				}
			}
			log.info( "AlmostVPNServer : starting : " + _version );
			log.info( "AlmostVPNServer : java.version = " + System.getProperty( "java.version"));
			
			for( int i = 0; i < args.length; i++ ) {
				log.info( "ToolServer starting : args[ " + i + " ] = " + args[ i ] );
				if( "-debug".equals( args[ i ] )) {
					_debugMode = true;
				} else if ( "-shell".equals( args[ i ] )) {
					_shellMode = true;
					i++;
					_shellPort = Integer.parseInt( args[ i ] );
					i++;
					_shellDev = args[ i ];
				} else if( args[ i ] != null ){
					_preferencesFilePath = args[i];
				}
			}
			
			if( _shellMode ) {
				runShellRepeater();
			} else {
				log.info( "AlmostVPNServer : plist " + _preferencesFilePath + "\n" );
		
				reloadConfiguration();
				
				_toolServer = new ToolServer();
				_toolServer.start();
				
				log.info( "AlmostVPNServer : running as " + System.getProperty( "user.name" ));
		
				_toolServer.event( DID_STARTUP_EVENT );
				
				_toolServer.join();
				
				_toolServer.event( "_WILL_SHUTDOWN_" );
		
				_toolServer.stop();
			}
		} catch( Throwable t ) {
			log.error( "AlmostVPNServer : " + t, t );
		}
	}
	
	private static void runShellRepeater() {
		log.info( "AlmostVPNServer : runShellRepeater : port = " + _shellPort );
		try {
			Socket s = new Socket( "localhost", _shellPort );
			InputStream is = s.getInputStream();
			OutputStream os = s.getOutputStream();
			
			InputStream consoleIn = new FileInputStream( "/dev/" + _shellDev );
			FileOutputStream consoleOut = new FileOutputStream( "/dev/" + _shellDev );
			
			DataCopier inOutCopier = new DataCopier( is, consoleOut )
				.setInteractive(true);
			DataCopier outInCopier = new DataCopier( consoleIn, os )
				.setInteractive(true);
			
			inOutCopier.start();
			outInCopier.start();
			
			inOutCopier.join();			
		} catch (Exception e) {
			log.error( "AlmostVPNServer : runShellRepeater", e );
		}
		
	}
	private static int _configuration_generation = 0;
	
	public static int configurationGeneration() {
		return _configuration_generation;
	}
	
	static Object _lock = new Object();
	
	private static void reloadConfiguration() {
		synchronized( _lock ) {
			File preferencesFile = new File( _preferencesFilePath );
			if( _preferencesFileTimestamp != preferencesFile.lastModified() ) {
				_configuration_generation++;
				AVPNConfiguration.loadFromFile( _preferencesFilePath );

				String userName = AVPNConfiguration.configurationProperty( "user-name" );
				if( userName != null ) System.setProperty( PathLocator.USER_NAME_PROPERTY, userName );
				
				String userHome = AVPNConfiguration.configurationProperty(  "user-home" );
				if( userHome != null ) System.setProperty( PathLocator.USER_HOME_PROPERTY, userHome );
	
				String appSupportPath = AVPNConfiguration.configurationProperty(  "application-support-path" );
				if( appSupportPath != null ) System.setProperty( PathLocator.APP_SUPPORT_PATH_PROPERTY, appSupportPath );				
	
				String preferencesPath = AVPNConfiguration.configurationProperty(  "preferences-path" );
				if( preferencesPath != null ) System.setProperty( PathLocator.PREFERENCES_PATH_PROPERTY, preferencesPath );				
	
				String resourcesPath = AVPNConfiguration.configurationProperty( "resources-path" );
				if( resourcesPath != null ) System.setProperty( PathLocator.RESOURCES_PATH_PROPERTY, resourcesPath );				
				
				String keychainPath = AVPNConfiguration.configurationProperty( "keychain-path" );
				if( keychainPath != null ) System.setProperty( PathLocator.KEYCHAIN_PATH_PROPERTY, keychainPath );				
				
				String sspPath = AVPNConfiguration.configurationProperty( "secure-store" );
				if( sspPath != null ) System.setProperty( PathLocator.SSP_PATH_PROPERTY, sspPath );				
				
				PathLocator.sharedInstance().resetEnv();
				
				startStopProxyServer();
			}
			_preferencesFileTimestamp = preferencesFile.lastModified(); 
		}
	}
	
	ToolServer() throws ToolException {
		_server = new JettyServer( this );
	}
	
	static void startProxyServer( ) {
		
	}
	private static void startStopProxyServer() {
		boolean runProxy = AVPNConfiguration.configurationProperty( "run-dynamic-proxy", "no" ).equals( "yes" );
		boolean shareProxy = AVPNConfiguration.configurationProperty( "share-dynamic-proxy", "no" ).equals( "yes" );
		int proxyPort =  Integer.parseInt( AVPNConfiguration.configurationProperty( "dynamic-proxy-port", "1080" ));
		boolean controlSystemSettings = AVPNConfiguration.configurationProperty( "control-system-settings", "no" ).equals( "yes" );
		
		if( runProxy ) {
			if( _dynamicProxy != null && ( _dynamicProxy.proxyPort() != proxyPort || _dynamicProxy.shareProxy() != shareProxy )) {
				_dynamicProxy.stop();
			} else if( _dynamicProxy != null ) {
				return;
			}
			
			try {
				_dynamicProxy = DynamicProxy.start( null, shareProxy, proxyPort, controlSystemSettings );
				log.info( "AlmostVPNServer : Running Dynamic Proxy on " + (shareProxy?"*":"127.0.0.1") + ":" + proxyPort );
			} catch (ProfileException e) {
				log.warn( "AlmostVPNServer : Fail to start Dynamic Proxy on "  + (shareProxy?"*":"127.0.0.1") + ":" + proxyPort, e );
			}
			
		} else {
			if( _dynamicProxy != null ) {
				log.info( "AlmostVPNServer : Dynamic Proxy Stopped" );
				_dynamicProxy.stop();
			}
		}
		
	}
	
	public static ToolServer toolServer() {
		return _toolServer;
	}
	
	public AVPNConfiguration configuration() {
		return _configuration;
	}
	
	public void start() throws ToolException {
		try {
			_server.start();
		} catch (Exception e) {
			throw new ToolException( "Fail to start Jetty server : " + e, e );
		}
	}
	
	public void stop() throws ToolException {
		try {
			_server.stop();
		} catch (Exception e) {
			throw new ToolException( "Fail to stop Jetty server : " + e, e );
		}
	}
	
	public void join() {
		while( JettyServer.server().isStarted()) {
			try { Thread.sleep(100); } catch (InterruptedException e1) { /* DO NOTHING */	}
		}
		while( JettyServer.server().isStarting()) {
			try { Thread.sleep(100); } catch (InterruptedException e1) { /* DO NOTHING */	}
		}
		while( JettyServer.server().isRunning()) {
			try { Thread.sleep(100); } catch (InterruptedException e1) { /* DO NOTHING */	}
		}
	}
	
	public static final String	DID_STARTUP_EVENT		= "_DID_STARTUP_";
	public static final String	WILL_SHUTODOWN_EVENT	= "_WILL_SHUTDOWN_";
	
	public static final String	DID_LOGIN_EVENT			= "_DID_LOGIN_";
	public static final String	WILL_LOGOUT_EVENT		= "_WILL_LOGOUT_";
	
	public static final String	DID_WAKEUP				= "_DID_WAKEUP_";
	public static final String 	WILL_SLEEP				= "_WILL_SLEEP_";
	
	public static final String	WILL_SWITCH_OUT			= "_WILL_SWITCH_OUT_";
	public static final String 	DID_SWITCH_IN			= "_DID_SWITCH_IN_";

	public static final String	AVPN_DID_STARTUP_EVENT	= "_AVPN_DID_STARTUP_";
	public static final String 	AVPN_WILL_SHUTDOWN_EVENT= "_AVPN_WILL_SHUTDOWN_";

	public static final String 	WILL_POWER_OFF			= "_WILL_POWER_OFF_";

	public void event( String event ) {
		ToolServer.reloadConfiguration();
		log.info( "Event : " + event );
		boolean doStart = false;
		int		targetRunMode = 0;
		boolean	considerStopOnFus = false;
		String reason = null;				
		
		if( DID_STARTUP_EVENT.equals( event )) {
			doStart = true;
			targetRunMode = 1;
			reason = "boot";
		} else if( WILL_SHUTODOWN_EVENT.equals( event )) {
			doStart = false;
			targetRunMode = 0;
			reason = "shutdown";
		} else if( DID_LOGIN_EVENT.equals( event )) {
			doStart = true;
			targetRunMode = 2;
			reason = "login";
		} else if( WILL_LOGOUT_EVENT.equals( event )) {
			doStart = false;
			targetRunMode = 2;
			reason = "logout";
		} else if( AVPN_DID_STARTUP_EVENT.equals( event )) {
			doStart = true;
			targetRunMode = 3;
			reason = "avpn start";
		} else if( AVPN_WILL_SHUTDOWN_EVENT.equals( event )) {
			doStart = false;
			targetRunMode = 3;
			reason = "avpn stop";
		} else if( DID_WAKEUP.equals( event )) {
			doStart = true;
			targetRunMode = 2;
			reason = "did wakeup";
		} else if( WILL_SLEEP.equals( event )) {
			doStart = false;
			targetRunMode = 0;
			reason = "will sleep";
		} else if( DID_SWITCH_IN.equals( event )) {
			doStart = true;
			targetRunMode = 2;
			reason = "did switched in";
		} else if( WILL_SWITCH_OUT.equals( event )) {
			doStart = false;
			targetRunMode = 0;
			considerStopOnFus = true;
			reason = "will switch out";
		} else {
			log.warn( "Unknown event : " + event );
			return;
		}
		
		NSDictionary[] profiles = AVPNConfiguration.allProfiles();
		for( int i = 0; i < profiles.length; i++ ) {
			String name = (String) profiles[ i ].objectForKey( "name");
			int runMode = profiles[ i ].intForKey( "run-mode", 0 );
			boolean stopOnFus = profiles[ i ].booleanForKey( "stop-on-fus", false );
			Profile profile = Profile.getProfile( name, doStart );
			
			if( profile == null ) {
				continue;
			}
			
			try {
				if( !  doStart ) { // DO STOP
					if( profile.isRunning() && (( targetRunMode == 0 ) || ( targetRunMode <= runMode ))) {
						if( considerStopOnFus ) {
							if( stopOnFus ) {
								profile.setWasRunning( true );
								log.info( "Stopping : " + name + " due to " + event );
								profile.stop( reason );
							}
						} else {
							profile.setWasRunning( true );
							log.info( "Stopping : " + name + " due to " + event );
							profile.stop( reason );
						}
					}
				} else {
					if( runMode == 0 ) {
						if( profile.wasRunning()) {
							log.info( "Starting : " + name + " due to " + event + " (was running)" );
							profile.start( reason );
						}
					} else if( targetRunMode >= runMode ){
						log.info( "Starting : " + name + " due to " + event );
						profile.start( reason );
					}
					profile.setWasRunning( false );
				}
			} catch ( ProfileException e ) {
				log.error( "Fail to process evnt " + event + " for profile " + name, e );
			}
		}
	}
	
	public void doExit() throws ToolException {
		doStopAllProfiles();
		if( _dynamicProxy != null ) {
			_dynamicProxy.stop();
		}
		try {
			_server.stop();
		} catch (Exception e) {
			throw new ToolException( "Fail to stop Jetty server : " + e, e );
		}		
	}

	final void startProfile( String name ) throws ProfileException {
		reloadConfiguration();

		Profile profile = Profile.getProfile( name, true );
		if( profile == Profile.NO_SUCH_PROFILE ) {
			log.warn( "No such profile : " + name );
		} else {
			log.info( "Profile name : " + name );
			profile.start( "User action" );
		}		
	}
	
	public NSDictionary doStartProfile( final String name ) throws ProfileException {
		Profile profile = Profile.getProfile( name, false );
		if(( profile != null ) && ( ! profile.isIdle())) {
			log.warn( "Profile is not idle :" + name );
		} else {
			Thread t = new Thread( new Runnable() {
				public void run() {
					try {
						startProfile( name );
					} catch (ProfileException e) {
						log.error( "Fail to start profile : " + name, e );
					}
				}				
			} );
			t.start();
		}
		return doReportStatus();
	}
	
	public NSDictionary doResumeProfile( final String name ) throws ProfileException {
		Profile profile = Profile.getProfile( name, false );
		if( profile == Profile.NO_SUCH_PROFILE ) {
			log.warn( "No such profile : " + name );
			return doReportStatus();
		}
		if(( profile != null ) && profile.isIdle() && profile.wasRunning() ) {
			doStartProfile( name );
		}
		return doReportStatus();
	}
	
	public NSDictionary doPauseProfile( String name ) throws ProfileException {
		Profile profile = Profile.getProfile( name, false );
		if( profile != null ) {
			if( profile == Profile.NO_SUCH_PROFILE ) {
				log.warn( "No such profile : " + name );
			} else if( profile == Profile.PROFILE_IS_NOT_RUNNING ){
				log.warn( "Profile is not running : " + name );
			} else {
				log.info( "Profile name : " + name );
				profile.stop( "User action", true);
			}
		}
		return doReportStatus();
	}
	
	public NSDictionary doStopProfile( String name ) throws ProfileException {
		Profile profile = Profile.getProfile( name, false );
		if( profile != null ) {
			if( profile == Profile.NO_SUCH_PROFILE ) {
				log.warn( "No such profile : " + name );
			} else if( profile == Profile.PROFILE_IS_NOT_RUNNING ){
				log.warn( "Profile is not running : " + name );
			} else {
				log.info( "Profile name : " + name );
				profile.stop( "User action");
			}
		}
		return doReportStatus();
	}
	
	public void doStopAllProfiles() {
		Profile.stopAllProfiles();
	}
	
	public NSDictionary doReportStatus() {
		return Profile.reportStatus();
	}

	public void saveSecret(String key, String value) {
		SecureStorageProvider.saveSecureObject( key, value );
	}

	public String checkSecret(String key) {
		return SecureStorageProvider.retriveSecureObject(key) == null ? "NO" : "YES";
	}

	public void forgetSecret(String key) {
		SecureStorageProvider.deleteSecureObject( key );
	}
	
	public String testLocation( String uuid ) {
		reloadConfiguration();
		
		String result = "OK";
		Model location = new Model( AVPNConfiguration.objectForUuid( uuid ));
		SSHSession session = null;
		try {
			session = new SSHSession( location.address(), location.port(), location.account().userName() );
			SessionConfigurator.configureSSHSession( session, location );
			
			session.getSession().setTimeout( 5000 );
			
			session.start();	
			result = session.getSession().isConnected() ? "OK" : "Fail to connect";
			session.stop();
		} catch (ProfileException e) {
			result = e.getMessage();
			if( session != null ) { try {
				session.stop();
			} catch (ProfileException e1) {
				// do nothing
			}}
		}
		
		return result;
	}
}
