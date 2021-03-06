//
//  LBIdentity.m
//  AlmostVPNPRO_bsd
//
//  Created by andrei tchijov on 12/16/05.
//  Copyright 2005 Leaping Bytes, LLC. All rights reserved.
//

#import "LBIdentity.h"
#import "LBFile.h"

@implementation LBIdentity
static LBIdentity* _sharedInstance;

+(LBIdentity*) sharedInstance {
	@synchronized( self ) {
		if( _sharedInstance == nil ) {
			_sharedInstance = [[ LBIdentity alloc ] init ];
		}
	}
	
	return _sharedInstance;
}

-(id) init {
	self = [ super init ];
	_dotSshPath = [ @"~/.ssh" stringByExpandingTildeInPath ];
	return self;
}

-(NSString*) dotSshPath {
	return _dotSshPath;
}
-(void) setDotSshPath: (NSString*) v {
	v = [ v copy ];
	[ _dotSshPath release ];
	_dotSshPath = v;
}

- (NSString*) pathToIdentityFile: (NSString*) name {
	return [ NSString stringWithFormat: @"%@/%@", _dotSshPath, name ];
}

-(NSArray*) identityFiles {
	return [ self identityFilesInFolder: _dotSshPath ];
}

-(NSArray*) identityFilesInFolder: (NSString*)path {
	NSArray* allFiles = [[ LBFile sharedInstance ] listFilesAt: path ];

	NSMutableArray* result = [ NSMutableArray array ];
	for( int i = 0; i < [ allFiles count ]; i++ ) {
		NSString* filePath = [ path stringByAppendingPathComponent: [ allFiles objectAtIndex: i ]];
		if( [ self isPrivateIdentityFile: filePath ] ) {
			[ result addObject: filePath ];
		}
	}
	return result;
}

-(NSDictionary*) newIdentityFile: (NSDictionary*) params {
	NSString* type = [ params valueForKey: @"type" ]; type = type == nil ? @"rsa" : type;
	NSString* bits = [ params valueForKey: @"bits" ]; bits = bits == nil ? @"1024" : bits;
	NSString* name = [ params valueForKey: @"name" ]; name = name == nil ? [ NSString stringWithFormat: @"id_%@", type ] : name;
	NSString* passphrase = [ params valueForKey: @"passphrase" ]; 
	NSString* comment = [ params valueForKey: @"comment" ]; 
	
	NSString* path = [ self pathToIdentityFile: name ];
	if( [[ LBFile sharedInstance ] fileExistsAtPath: path ] ) {
		int rc = NSRunAlertPanel( 
			@"File already exists",
			@"Are you sure that you want to replace file:\n %@ \nwith new identity?\nYou will not be able to 'UNDO' this operation.", 
			@"Yes", nil, @"Cancel",
			path
		);
		if( rc == 1 ) {
			[[ LBFile sharedInstance ] deleteFile: [ self pathToPublicKeyPath: path ]];
			[[ LBFile sharedInstance ] deleteFile: [ self pathToPrivateKeyPath: path ]];
		} else {
			return nil;
		}
	}
	NSMutableArray* args = [ NSMutableArray array ];
	[ args addObject: @"-b" ]; [ args addObject: bits ];
	[ args addObject: @"-t" ]; [ args addObject: type ];
	if( passphrase != nil ) {
		[ args addObject: @"-N" ]; [ args addObject: passphrase ];
	} else {
		[ args addObject: @"-N" ]; [ args addObject: @"" ];
	}
	if( comment != nil ) {
		[ args addObject: @"-C" ]; [ args addObject: comment ];
	}
	[ args addObject: @"-f" ]; [ args addObject: path ];

	NSTask* task = [ NSTask launchedTaskWithLaunchPath: @"/usr/bin/ssh-keygen" arguments: args ]; 
	[ task waitUntilExit ];
	NSDictionary* result;
	if( [ task terminationStatus ] == 0 ) {
		result = [[ self queryIdentityFile:  path ] mutableCopy ];
		[ result setValue: [ path stringByAbbreviatingWithTildeInPath ] forKey: @"path" ];
		[ result setValue: [ NSString stringWithFormat: @"%d", [ task terminationStatus ]] forKey: @"exitCode" ];
	} else {
		result = nil;
	}
	
	return result;
}

-(NSString*) pathToPublicKeyPath: (NSString*) path {
	NSString* publicKeyPath = [ path hasSuffix: @".pub" ] ? path : [ path stringByAppendingString: @".pub" ];
	return publicKeyPath;
}

-(NSString*) pathToPrivateKeyPath: (NSString*) path {
	NSString* privateKeyPath = ! [ path hasSuffix: @".pub" ] ? path : [ path substringToIndex: [ path length ] - 4 ];
	return privateKeyPath;
}

//-(BOOL) isIdentityFile: (NSString*) path {
//	// TODO : This is very lame way to test.  Need to improve later.
//	NSString* privateKeyPath = [ self pathToPrivateKeyPath: path ];
//	NSString* publicKeyPath = [ self pathToPublicKeyPath: path ];
//	
//	return [[ LBFile sharedInstance ] fileExistsAtPath: privateKeyPath ] && [[ LBFile sharedInstance ] fileExistsAtPath: publicKeyPath ];
//}

-(BOOL) isPublicIdentityFile: (NSString*) path {
	NSTask* checkIt = [ NSTask 
		launchedTaskWithLaunchPath: @"/usr/bin/ssh-keygen" 
		arguments: [ NSArray arrayWithObjects: 
			@"-B",
			@"-f",
			[ path stringByExpandingTildeInPath ],
			nil
		]
	];
	[ checkIt waitUntilExit ];
	return [ checkIt terminationStatus ] == 0;
	// return [ path isEqual: [ self pathToPublicKeyPath: path ]] && [ self isIdentityFile: path ];
}

-(BOOL) isPrivateIdentityFile: (NSString*) path {
	return [ self isPrivateIdentityFile: path withPassphrase: nil ];
}
-(BOOL) isPrivateIdentityFile: (NSString*) path withPassphrase: (NSString*) passphrase {
	NSDictionary* fileAttributes = [
		[ NSFileManager defaultManager ] 
			fileAttributesAtPath: [ path stringByExpandingTildeInPath ]
			        traverseLink: NO 
	];
	if( fileAttributes == nil ) {
		return NO;
	}
	int permissions = [ fileAttributes filePosixPermissions ];
	if( ( permissions & 077 ) != 0 ) {
		NSRunAlertPanel( 
			@"Can not import identity", 
			[ NSString stringWithFormat: 
				@"Identity file '%@' has incorrect permissions.\n"
				"Private key should be readable only by owner\n", path ],
			@"OK", nil, nil 
		);
	
		return NO;
	}

	if( [ passphrase length ] == 0 ) {
		NSTask* checkIt = [[ NSTask alloc ] init ];
		[ checkIt setLaunchPath:  @"/usr/bin/ssh-keygen" ];
		[ checkIt setArguments: [ NSArray arrayWithObjects: 
				@"-e",
				@"-f",
				[ path stringByExpandingTildeInPath ],
				nil
			]
		];
		[ checkIt setStandardInput: [ NSFileHandle fileHandleWithNullDevice ]];
		//[[ checkIt standardInput ] writeData: [ @"\n\n" dataUsingEncoding: NSUTF8StringEncoding ]];
		[ checkIt launch ];
		[ checkIt waitUntilExit ];
		BOOL result = [ checkIt terminationStatus ] == 0;
		return result;
	} else {
		return YES;
	}
//	return [ path isEqual: [ self pathToPrivateKeyPath: path ]] && [ self isIdentityFile: path ];
}

/*
	fingerprint:
	bits:
		ssh-keygen -l -f <file>         
		1024 84:ce:ce:57:67:e8:2d:05:99:88:2d:d0:bb:ed:ef:d9 atchijov@buster.local
	
	digest:
		ssh-keygen -B -f <file>
		1024 xuvan-riban-rynif-dybiv-syzeh-cegub-conuz-kuvop-perod-sefam-bexex id_dsa.pub
	
	
	type:
		ssh-keygen -y -f <file>
		rsa1
			1024 35 ....		
		rsa
			ssh-rsa AAAAB...		
		dsa
			ssh-dss AAAA...
			
	type:
		ssh-keygen -e -f id_rsa_passphrase     
		rsa
			---- BEGIN SSH2 PUBLIC KEY ----
			Comment: "1024-bit RSA, converted from OpenSSH by ..."
			AAAA...
			...=
			---- END SSH2 PUBLIC KEY ----

		dsa
			---- BEGIN SSH2 PUBLIC KEY ----
			Comment: "1024-bit DSA, converted from OpenSSH by ..."
			AAAA...
			...=
			---- END SSH2 PUBLIC KEY ----
			
		rsa1
			command fails with termination code == 1
*/
-(NSDictionary*) queryIdentityFile: (NSString*)path {
	return [ self queryIdentityFile: path withPassphrase: nil ];
}
-(NSDictionary*) queryIdentityFile: (NSString*)path withPassphrase: (NSString*) passphrase {
	BOOL isPrivateFile = [ self isPrivateIdentityFile: path withPassphrase: passphrase ];
	BOOL isPublicFile = [ self isPublicIdentityFile: path ];
	
	if(( ! isPrivateFile ) && ( ! isPublicFile )) {
		return nil;
	}
	
	NSString* publicPath = [ self pathToPublicKeyPath: path ];
	NSString* privatePath = [ self pathToPrivateKeyPath: path ];
	
	isPrivateFile = [[ NSFileManager defaultManager ] fileExistsAtPath: privatePath ];
	isPublicFile  = [[ NSFileManager defaultManager ] fileExistsAtPath: publicPath ];

	NSString* fingerprint;
	NSString* bits;
	NSString* digest;
	NSString* type;
	
	NSMutableString* option;
	NSArray* arguments = [ NSArray arrayWithObjects:
		option = [ NSMutableString string ],
		@"-f",
		path,
		nil
	];
	NSTask* task;
	NSPipe* pipe;
	NSData* data;
	NSString* string;
	NSArray* parts;
	
	/*
		get fingerprint and bits
	*/
	if( isPublicFile ) {
		task = [[ NSTask alloc ] init ];
			[ task setLaunchPath: @"/usr/bin/ssh-keygen" ];
			[ task setArguments: arguments ];
			[ task setStandardOutput: pipe = [ NSPipe pipe ]];
			[ option setString: @"-l" ];
		[ task launch ]; [ task waitUntilExit ];
		if( [ task terminationStatus ] != 0 ) {
			return nil;
		} 
		
		data = [[ pipe fileHandleForReading ] readDataToEndOfFile ];
		parts = [[ NSString stringWithCString: [ data bytes ] length: [ data length ]] componentsSeparatedByString: @" " ];
		if( [ parts count ] < 3 ) {
			return nil;
		}
		fingerprint = [ parts objectAtIndex: 1 ];
	} else {
		fingerprint = @"Can not find matching public key";
	}
	
	/*
		get digest
	*/
	if( isPublicFile ) {
		task = [[ NSTask alloc ] init ];
			[ task setLaunchPath: @"/usr/bin/ssh-keygen" ];
			[ task setArguments: arguments ];
			[ task setStandardOutput: pipe = [ NSPipe pipe ]];
			[ option setString: @"-B" ];
		[ task launch ]; [ task waitUntilExit ];
		if( [ task terminationStatus ] != 0 ) {
			[ task release ];
			return nil;
		} 
		[ task release ];
		data = [[ pipe fileHandleForReading ] readDataToEndOfFile ];
		parts = [[ NSString stringWithCString: [ data bytes ] length: [ data length ]] componentsSeparatedByString: @" " ];
		if( [ parts count ] < 3 ) {
			return nil;
		}
		digest = [ parts objectAtIndex: 1 ];
	} else {
		digest = @"Can not find matching public key";
	}
	/*
		get type
	*/
	if( [ passphrase length ] != 0 ) {
		string = [ NSString stringWithContentsOfFile: path ];
		if( [ string rangeOfString: @"BEGIN RSA" ].length > 0 ) {
			type = @"rsa";
		} else if( [ string rangeOfString: @"BEGIN DSA" ].length > 0 ) {
			type = @"dsa";
		} 
	} else {
		task = [[ NSTask alloc ] init ];
			[ task setLaunchPath: @"/usr/bin/ssh-keygen" ];
			[ task setArguments: arguments ];
			[ task setStandardOutput: pipe = [ NSPipe pipe ]];
			[ option setString: @"-e" ];
		[ task launch ]; [ task waitUntilExit ];
		if( [ task terminationStatus ] == 1 ) {
			type = @"rsa1";
		} else if( [ task terminationStatus ] != 0 ) {
			[ task release ];
			return nil;
		} 
		[ task release ];
		data = [[ pipe fileHandleForReading ] readDataToEndOfFile ];
		string =[ NSString stringWithCString: [ data bytes ] length: [ data length ]];
		if( [ string rangeOfString: @" RSA," ].length > 0 ) {
			type = @"rsa";
		} else if( [ string rangeOfString: @" DSA," ].length > 0 ) {
			type = @"dsa";
		} 
	}
	
	
	NSRange commentRange = [ string rangeOfString: @"Comment: \"" ];
	NSRange dashBitRange = [ string rangeOfString: @"-bit" ];
	
	if( commentRange.location != NSNotFound && dashBitRange.location != NSNotFound ) {
		int sizeRangeLocation = commentRange.location + commentRange.length;
		NSRange sizeRange = NSMakeRange( sizeRangeLocation, dashBitRange.location - sizeRangeLocation );
		bits = [ string substringWithRange: sizeRange ];	
	} else {
		bits = @"unknown";
	}
	
	NSDictionary* result = [ NSDictionary dictionaryWithObjectsAndKeys:
		type, @"type",
		bits, @"bits",
		fingerprint, @"fingerprint",
		digest, @"digest",
		nil
	];
	
	return result;
}

@end
