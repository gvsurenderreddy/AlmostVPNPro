// MOExtendedMenu.m
// MOKit
//
// Copyright © 2003-2005, Mike Ferris.  All rights reserved.
// See bottom of file for license and disclaimer.

#import "MOExtendedMenu.h"
#import "MOExtendedMenuItem.h"

@implementation MOExtendedMenu

- (void)update {
    // Reset everything to defaults.
    NSArray *items = [self itemArray];
    unsigned i, c = [items count];
    Class itemClass = [MOExtendedMenuItem class];
    
    for (i=0; i<c; i++) {
        MOExtendedMenuItem *curItem = [items objectAtIndex:i];
        if ([curItem isKindOfClass:itemClass]) {
            [curItem resetToDefaults];
        }
    }

    [super update];
}

@end


/*
 This file contains Original Code and/or Modifications of Original Code as defined in and that are subject to the Ferris Public Source License Version 1.2 (the 'License'). You may not use this file except in compliance with the License. Please obtain a copy of the License at http://mokit.sourceforge.net/License.html and read it before using this file.

 The Original Code and all software distributed under the License are distributed on an 'AS IS' basis, WITHOUT WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED, AND MIKE FERRIS HEREBY DISCLAIMS ALL SUCH WARRANTIES, INCLUDING WITHOUT LIMITATION, ANY WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, QUIET ENJOYMENT OR NON-INFRINGEMENT. Please see the License for the specific language governing rights and limitations under the License.
 */
