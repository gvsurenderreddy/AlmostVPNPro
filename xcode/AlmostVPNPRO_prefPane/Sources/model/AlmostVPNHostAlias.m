//
//  AlmostVPNHostAlias.m
//  AlmostVPNPRO_model
//
//  Created by andrei tchijov on 12/12/05.
//  Copyright 2005 Leaping Bytes, LLC. All rights reserved.
//

#import "AlmostVPNHostAlias.h"

#define _ALIAS_NAME_KEY_		@"alias-name"
#define _ALIAS_ADDRESS_KEY_		@"alias-address"
#define _RECURSIVE_KEY_			@"recursive"

#define _AUTO_ALIAS_ADDRESS_	@"<auto>"

@implementation AlmostVPNHostAlias

- (id) initWithDictionary: (NSDictionary*) aDictionary {
	[ super initWithDictionary: aDictionary ];

	[ self setObject: [ aDictionary objectForKey: _ALIAS_NAME_KEY_ ] forKey: _ALIAS_NAME_KEY_ ];
	[ self setObject: [ aDictionary objectForKey: _ALIAS_ADDRESS_KEY_ ] forKey: _ALIAS_ADDRESS_KEY_ ];
	[ self setObject: [ aDictionary objectForKey: _RECURSIVE_KEY_ ] forKey: _RECURSIVE_KEY_ ];
	
	[ AlmostVPNHostAlias performOnDelegate: OBJECT_WAS_INIT_WITH_DICTIONARY withObject: self ];	
	return self;
}

- (NSString*) aliasName {
	return [ self stringValueForKey: _ALIAS_NAME_KEY_ ];
}

- (void) setAliasName: (NSString*) v {
	[ self setStringValue: v forKey: _ALIAS_NAME_KEY_ ];
}

+ (NSString*) aliasNameFromRealName: (NSString*) realName {
	if( [ realName rangeOfString: @"." ].location == NSNotFound ) {
		return [ NSString stringWithFormat: @"%@-avpn", realName ];
	} else {
		NSArray* realNameParts = [ realName componentsSeparatedByString: @"." ];
		NSMutableString* result = [ NSMutableString string ];
		for( int i = 0; i < [ realNameParts count ]; i++ ) {
			if( i > 0 ) {
				[ result appendString: @"-" ];
			}
			[ result appendString: [ realNameParts objectAtIndex: i ]];
		}
		[ result appendString: @"-avpn" ];
		return result;
	}
}

- (NSString*) aliasAddress {
	return [ self stringValueForKey: _ALIAS_ADDRESS_KEY_ ];
}

- (void) setAliasAddress: (NSString*) v {
	[ self setStringValue: v forKey: _ALIAS_ADDRESS_KEY_ ];
}

- (NSNumber*) autoAliasAddress {
	return [ NSNumber numberWithBool: [[ self aliasAddress ] isEqual: _AUTO_ALIAS_ADDRESS_ ]];
}

- (void) setAutoAliasAddress: (NSNumber*) v {
	BOOL boolV = [ v boolValue ];
	
	if( boolV != [[ self autoAliasAddress ] boolValue ] ) {
		if( boolV ) {
			[ self setAliasAddress: _AUTO_ALIAS_ADDRESS_ ];
		} else {
			[ self setAliasAddress: [(AlmostVPNHost*)[ self referencedObject ] address ]];
		}
	}
}

- (NSNumber*) recursiveHostAlias {
	return [ self booleanObjectForKey: _RECURSIVE_KEY_ ];
}

- (void) setRecursiveHostAlias: (NSNumber*) v {
	[ self setBooleanObject: v forKey: _RECURSIVE_KEY_ ];
}

- (NSArray*) resources {
	return [(AlmostVPNHost*)[ self referencedObject ] resources ];
}

@end
