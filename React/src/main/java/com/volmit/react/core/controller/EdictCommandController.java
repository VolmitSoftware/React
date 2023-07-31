/*
 *  Copyright (c) 2016-2025 Arcane Arts (Volmit Software)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.volmit.react.core.controller;

import art.arcane.edict.Edict;
import com.volmit.react.React;
import com.volmit.react.util.plugin.IController;

public class EdictCommandController implements IController {
    private static final String PKG = "com.volmit.react.";
    private static final String CORE = PKG + "core.";
    private static final String CONTENT = PKG + "content.";
    private transient Edict edict;

    @Override
    public void start() {

    }

    @Override
    public boolean autoRegister() {
        return false;
    }

    @Override
    public void stop() {

    }

    @Override
    public void postStart() {
        edict = Edict.builder()
                .parserPackage(CONTENT + "command.parser")
                .contextResolverPackage(CONTENT + "command.context")
                .build().registerPackage(CONTENT + "command")
                .registerPackage(CONTENT + "action")
                .registerPackage(CONTENT + "feature")
                .registerPackage(CONTENT + "tweak")
                .registerPackage(CORE + "controller")
                .initialize(React.instance.getCommand("react"));
    }

    @Override
    public String getId() {
        return "command";
    }
}
