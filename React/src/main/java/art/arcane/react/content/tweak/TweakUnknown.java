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

package art.arcane.react.content.tweak;

import art.arcane.react.api.tweak.ReactTweak;

@art.arcane.react.util.project.config.ConfigDescription("Configuration for Unknown tweak. Placeholder tweak used when a requested tweak ID is missing or unavailable.")
public class TweakUnknown extends ReactTweak {
  public static final String ID = "unknown";

  public TweakUnknown() {
    super(ID);
  }

  @Override
  public boolean autoRegister() {
    return false;
  }
}
