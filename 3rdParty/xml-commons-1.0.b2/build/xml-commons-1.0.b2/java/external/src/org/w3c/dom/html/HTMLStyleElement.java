/*
 * Copyright (c) 2000 World Wide Web Consortium,
 * (Massachusetts Institute of Technology, Institut National de
 * Recherche en Informatique et en Automatique, Keio University). All
 * Rights Reserved. This program is distributed under the W3C's Software
 * Intellectual Property License. This program is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.
 * See W3C License http://www.w3.org/Consortium/Legal/ for more details.
 */

package org.w3c.dom.html;

/**
 * Style information. A more detailed style sheet object model is planned to 
 * be defined in a separate document. See the STYLE element definition in 
 * HTML 4.0.
 * <p>See also the <a href='http://www.w3.org/TR/2000/WD-DOM-Level-1-20000929'>Document Object Model (DOM) Level 1 Specification (Second Edition)</a>.
 */
public interface HTMLStyleElement extends HTMLElement {
    /**
     * Enables/disables the style sheet. 
     */
    public boolean getDisabled();
    public void setDisabled(boolean disabled);

    /**
     * Designed for use with one or more target media. See the media attribute 
     * definition in HTML 4.0.
     */
    public String getMedia();
    public void setMedia(String media);

    /**
     *  The content type of the style sheet language. See the type attribute 
     * definition in HTML 4.0.
     */
    public String getType();
    public void setType(String type);

}
