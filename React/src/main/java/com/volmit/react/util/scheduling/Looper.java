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

package com.volmit.react.util.scheduling;

import com.volmit.react.React;

public abstract class Looper extends Thread {
    @SuppressWarnings("BusyWait")
    public void run() {
        while (!interrupted()) {
            try {
                long m = loop();

                if (m < 0) {
                    break;
                }

                //noinspection BusyWait
                Thread.sleep(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            } catch (Throwable e) {
                e.printStackTrace();
                e.printStackTrace();
            }
        }

        React.debug("React Thread " + getName() + " Shutdown.");
    }

    protected abstract long loop();
}
