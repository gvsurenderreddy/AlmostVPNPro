//
//  ForPayManager.m
//  AlmostVPNPRO_prefPane
//
//  Created by andrei tchijov on 6/7/06.
//  Copyright 2006 Leaping Bytes, LLC. All rights reserved.
//

#import "ForPayManager.h"
#import "PathLocator.h"
#import "AlmostVPNConfigurationController.h"
#import "AlmostVPNModel.h"

static ForPayManager*		_sharedInstance = nil;

@implementation ForPayManager

+ (void) initialize {
	[ self sharedInstance ];
	[self 
		setKeys:[NSArray arrayWithObjects:@"indicator", nil ]	
		triggerChangeNotificationsForDependentKey:@"forPayFeaturesUsed"
	];
	[self 
		setKeys:[NSArray arrayWithObjects:@"indicator", nil ]	
		triggerChangeNotificationsForDependentKey:@"indicatorToolTip"
	];	
}

+ (ForPayManager*) sharedInstance {
	if( _sharedInstance == nil ) {
		_sharedInstance = [[ ForPayManager alloc ] init ];
	}
	return _sharedInstance;
}

- (id) init {
	_forPayCounter = 0;
	_profile = nil;
	_profileName = @"Activation";
	_vhostCount = 0;
	
	return self;
}

+ (void) forPayFeature: (NSString*) comment {
	[ _sharedInstance forPayFeature: comment ];
}
- (void) forPayFeature: (NSString*) comment {
	_forPayCounter++;
	
	NSString* eventString = [ NSString stringWithFormat: 
		@"PAY:%ud|%@|%@|%@", (long)([[NSDate date ] timeIntervalSince1970 ]) * 1000, _profileName, @"900", comment ];
		
	[[ AlmostVPNConfigurationController sharedInstance ] addServerEvent: eventString ];
}

- (void) newProfile: (AlmostVPNProfile*) profile {
	[ profile retain ];
	[ _profile release ];
	_profile = profile;
	
	_profileName = [ profile name ];
}

- (void) evalResource: (AlmostVPNObjectRef*) resourceRef {
	AlmostVPNResource* resource = (AlmostVPNResource*)[ resourceRef referencedObject ];
	AlmostVPNLocation* location = (AlmostVPNLocation*)[ resource location ];
	
	if( ! [ location direct ] ) {
		[ self forPayFeature: [ NSString stringWithFormat: @"Multi-hope tunnel to access %@", [ resource fullName ]] ];
	}
}

- (void) evalTunnel: (AlmostVPNTunnel*) resourceRef {
	AlmostVPNLocation* source = [ resourceRef sourceLocation ];
	if( ! [ source direct ] ) {
		[ self forPayFeature: [ NSString stringWithFormat: @"Multi-hope tunnel %@", [ resourceRef fullName ]] ];
	}
}

- (void) evalDrive: (AlmostVPNDriveRef*) resourceRef {
	AlmostVPNDrive* resource = (AlmostVPNDrive*)[ resourceRef referencedObject ];
	
	if( [[ resourceRef useBonjour ] boolValue ] ) {
		[ self forPayFeature: [ NSString stringWithFormat: @"Use Bonjour with %@", [ resource fullName ] ] ];
	}
}

- (void) evalPrinter: (AlmostVPNPrinterRef*) resourceRef {
	AlmostVPNPrinter* resource = (AlmostVPNPrinter*)[ resourceRef referencedObject ];
	[ self forPayFeature: [ NSString stringWithFormat: @"Printer %@", [ resource fullName ] ]];
}

- (void) evalFile: (AlmostVPNFileRef*) resourceRef {
	AlmostVPNFile* resource = (AlmostVPNFile*)[ resourceRef referencedObject ];
	[ self forPayFeature: [ NSString stringWithFormat: @"File %@", [ resource fullName ] ]];
}

- (void) evalBonjour: (AlmostVPNBonjourRef*) resourceRef {
	AlmostVPNBonjour* resource = (AlmostVPNBonjour*)[ resourceRef referencedObject ];
	[ self forPayFeature: [ NSString stringWithFormat: @"Bonjour %@", [ resource fullName ] ]];
}

- (void) evalRC: (AlmostVPNRemoteControlRef*) resourceRef {
	AlmostVPNRemoteControl* resource = (AlmostVPNRemoteControl*)[ resourceRef referencedObject ];
	[ self forPayFeature: [ NSString stringWithFormat: @"Remote Control %@", [ resource fullName ] ]];
}

- (void) evalHostAlias: (AlmostVPNHostAlias*) resourceRef {
	AlmostVPNHost* host = (AlmostVPNHost*)[ resourceRef referencedObject ];
	
	_vhostCount ++;
	if( _vhostCount > 1 ) {
		[ self forPayFeature: @"More then one Host Alias" ];
	}
	
	NSArray* children = [ host children ];
	
	for( int i = 0; i < [ children count ]; i++ ) {
		AlmostVPNObject* child = [ children objectAtIndex: i ];
		
		if( [ child isKindOfClass: [ AlmostVPNDrive class ]] ) {
			if( [[ (AlmostVPNDrive*)child drivePath ] length ] == 0 ) {
				[ self forPayFeature: [ NSString stringWithFormat: @"Use Bonjour with %@", [ child fullName ] ] ];
			}
		} else
		if( [ child isKindOfClass: [ AlmostVPNPrinter class ]] ) {
			[ self forPayFeature: [ NSString stringWithFormat: @"Printer %@", [ child fullName ] ] ];
		} else
		if( [ child isKindOfClass: [ AlmostVPNFile class ]] ) {
			[ self forPayFeature: [ NSString stringWithFormat: @"File %@", [ child fullName ] ] ];
		} else 
		if( [ child isKindOfClass: [ AlmostVPNRemoteControl class ]] ) {
			[ self forPayFeature: [ NSString stringWithFormat: @"Remote Control %@", [ child fullName ] ] ];
		} else 
		if( [ child isKindOfClass: [ AlmostVPNBonjour class ]] ) {
			[ self forPayFeature: [ NSString stringWithFormat: @"Bonjour %@", [ child fullName ] ] ];
		} 
	}	
}

+ (void) evalConfiguration {
	[ _sharedInstance evalConfiguration ];
}
- (void) evalConfiguration {
	[ self willChangeValueForKey: @"indicator" ];
	_vhostCount = 0;
	_forPayCounter = 0;
	NSArray* profiles = [[ AlmostVPNConfigurationController sharedInstance ] profiles ];
	for( int i = 0; i < [ profiles count ]; i++ ) {
		AlmostVPNProfile* profile = [ profiles objectAtIndex: i ];
		
		[ self newProfile: profile ];
		
		if( [[ profile stopOnFUS ] boolValue ] ) {
			[ self forPayFeature: @"Stop on \"Fast User Switch\""  ];
		}
		if( [[ profile fireAndForget ] boolValue ] ) {
			[ self forPayFeature: @"Fire and Forget" ];
		}
		if( [[ profile runMode ] isEqual: _RUN_AT_BOOT_ ] ) {
			[ self forPayFeature: @"Start at BOOT" ];
		}
		if( [[ profile runMode ] isEqual: _RUN_AT_START_ ] ) {
			[ self forPayFeature: @"Start at Preference Pane Start" ];
		}
		
		NSArray* resources = [ profile resources ];
		
		for( int j = 0; j < [ resources count ]; j++ ) {
			AlmostVPNObjectRef* resource = [ resources objectAtIndex: j ];
			[ self evalResource: resource ];
			
			if( [ resource isKindOfClass: [ AlmostVPNTunnel class ]] ) {
				[ self evalTunnel: (AlmostVPNTunnel*)resource ];
			} else
			if( [ resource isKindOfClass: [ AlmostVPNDriveRef class ]] ) {
				[ self evalDrive: (AlmostVPNDriveRef*)resource ];
			} else
			if( [ resource isKindOfClass: [ AlmostVPNPrinterRef class ]] ) {
				[ self evalPrinter: (AlmostVPNPrinterRef*)resource ];
			} else
			if( [ resource isKindOfClass: [ AlmostVPNFileRef class ]] ) {
				[ self evalFile: (AlmostVPNFileRef*)resource ];
			} else
			if( [ resource isKindOfClass: [ AlmostVPNBonjourRef class ]] ) {
				[ self evalBonjour: (AlmostVPNBonjourRef*)resource ];
			} else
			if( [ resource isKindOfClass: [ AlmostVPNRemoteControlRef class ]] ) {
				[ self evalRC: (AlmostVPNRemoteControlRef*)resource ];
			} else
			if( [ resource isKindOfClass: [ AlmostVPNHostAlias class ]] ) {
				[ self evalHostAlias: (AlmostVPNHostAlias*)resource ];
			} else {
				// Stange??
			}
		}
	}
	if( _forPayCounter != 0 ) {		
		[[ AlmostVPNConfigurationController sharedInstance ] setTopLevelTab: @"Monitor" ];
		[[ PathLocator soundNamed: @"CashRegister" ] play ];	
	}
	[ self didChangeValueForKey: @"indicator" ];
}

- (NSString*) indicator {
	if( _forPayCounter == 0 ) {
		return @"";
	} else if( _forPayCounter <= 2 ) {
		return @"$";
	} else if( _forPayCounter <= 4 ) {
		return @"$$";
	} else if( _forPayCounter <= 8 ) {
		return @"$$$";
	} else if( _forPayCounter <= 10 ) {
		return @"$$$$";
	} else {
		return @"$$$$$";
	}
}

+ (int) forPayCounter {
	return [ _sharedInstance forPayCounter ];
}

- (int) forPayCounter {
	return _forPayCounter;
}

- (id) forPayFeaturesUsed {
	BOOL result = ([ self forPayCounter ] > 0 ) && ( ! [[ AlmostVPNConfigurationController sharedInstance ] activationOK ]);
	
	return [ NSNumber numberWithBool: result ];
}

- (void) setForPayFeaturesUsed: (id) v {
}

- (void) setIndicator: ( NSString*) v {
}

- (NSString*) indicatorToolTip {
	if( _forPayCounter == 0 ) {
		return @"";
	}
	NSString* format;
	
	if( _forPayCounter == 1 ) {
		format = @"You are using one 'for pay' feature.\nLook into 'Monitor/Log' for details.\nClick to buy.";
	} else {
		format = @"You are using %d 'for pay' features.\nLook into 'Monitor/Log' for details.\nClick to buy.";
	}
	
	return [ NSString stringWithFormat: format, _forPayCounter ];
}

- (void) setIndicatorToolTip: (NSString*)  v {
}
@end
