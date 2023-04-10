/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.s1tbx.dat;

import org.esa.s1tbx.commons.S1TBXSetup;
import org.openide.modules.OnStart;

/**
 * Handle OnStart for module
 */
public class S1tbxRCPModule {

    @OnStart
    public static class StartOp implements Runnable {

        @Override
        public void run() {
            S1TBXSetup.installColorPalettes(this.getClass(), "org/esa/s1tbx/dat/auxdata/color_palettes/");
        }
    }
}
