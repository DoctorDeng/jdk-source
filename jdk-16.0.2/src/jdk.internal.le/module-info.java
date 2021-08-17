/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * Internal API for line editing
 *
 * @since 9
 */
module jdk.internal.le {
    exports jdk.internal.org.jline.keymap to jdk.jshell;
    exports jdk.internal.org.jline.reader to jdk.jshell;
    exports jdk.internal.org.jline.reader.impl to jdk.jshell;
    exports jdk.internal.org.jline.reader.impl.completer to jdk.jshell;
    exports jdk.internal.org.jline.reader.impl.history to jdk.jshell;
    exports jdk.internal.org.jline.terminal to jdk.jshell;
    exports jdk.internal.org.jline.terminal.impl to jdk.jshell;
    exports jdk.internal.org.jline.terminal.spi to jdk.jshell;
    exports jdk.internal.org.jline.utils to jdk.jshell;

    uses jdk.internal.org.jline.terminal.spi.JnaSupport;
    provides jdk.internal.org.jline.terminal.spi.JnaSupport with jdk.internal.org.jline.terminal.impl.jna.JnaSupportImpl;
}