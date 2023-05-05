/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package com.volmit.react.util;

import java.io.File;
import java.util.List;

public class ReactiveFolder {
    private final File folder;
    private final Consumer3<List<File>, List<File>, List<File>> hotload;
    private FolderWatcher fw;

    public ReactiveFolder(File folder, Consumer3<List<File>, List<File>, List<File>> hotload) {
        this.folder = folder;
        this.hotload = hotload;
        this.fw = new FolderWatcher(folder);
        fw.checkModified();
    }

    public void checkIgnore() {
        fw = new FolderWatcher(folder);
    }

    public void check() {
        boolean modified = false;

        if(fw.checkModified()) {
            for(File i : fw.getCreated()) {
                if(i.getName().endsWith(".iob") || i.getName().endsWith(".json")) {
                    modified = true;
                    break;
                }
            }

            if(!modified) {
                for(File i : fw.getChanged()) {
                    if(i.getName().endsWith(".iob") || i.getName().endsWith(".json")) {
                        modified = true;
                        break;
                    }
                }
            }

            if(!modified) {
                for(File i : fw.getDeleted()) {
                    if(i.getName().endsWith(".iob") || i.getName().endsWith(".json")) {
                        modified = true;
                        break;
                    }
                }
            }
        }

        if(modified) {
            hotload.accept(fw.getCreated(), fw.getChanged(), fw.getDeleted());
        }

        fw.checkModified();
    }
}
