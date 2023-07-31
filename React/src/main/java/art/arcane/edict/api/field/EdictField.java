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

package art.arcane.edict.api.field;

import art.arcane.edict.api.endpoint.EdictEndpoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * The EdictField class represents a field within an Edict command in the Edict command handling system.
 * Each field has a list of names it can be identified by, a type which defines the kind of data it can hold,
 * and a default value which can be used when no other value is provided.
 * <br><br>
 * This class is typically used in conjunction with the {@link EdictEndpoint} class to define a command's expected inputs.
 */
@AllArgsConstructor
@Builder
@Data
public class EdictField {
    /**
     * The names by which this field can be identified. The field can be referred to by any of these names.
     */
    @Singular
    private final List<String> names;
    /**
     * The type of data that this field can hold. By default, it is a String.
     */
    @Builder.Default
    private final Class<?> type = String.class;
    /**
     * The description of the command. This is used in the help command.
     */
    @Builder.Default
    private String description = "No Description";
    @Builder.Default
    private boolean contextual = false;
    /**
     * Require a permission for the command to be executed.
     * <br><br>
     * Parameter Edge Cases for when using this annotation on method parameters:
     * <ul>
     *     <li>If the parameter is contextual, it will be null if they lack the parameter even if there is a context</li>
     *     <li>If the parameter is required, this command will fail the permission check OR fail the required check. Do not do this.</li>
     *     <li>If the parameter is non-null but the confidence is INVALID, it will be set to null and be allowed</li>
     *     <li>If the parameter is non-null, LOW OR HIGH, permission error will happen and it wont run</li>
     * </ul>
     */
    @Singular
    private List<String> permissions;
    /**
     * The default string to parse into the command if no input is provided.
     */
    private String defaultValue;
    /**
     * Whether or not this field is required. If it is, the command will fail if no input is provided. Defaults to false
     */
    @Builder.Default
    private boolean required = false;

    public String getUsage() {
        return (required ? "<" : "[") + getType().getSimpleName() + " " + names.stream().findFirst().orElse("") + (required ? ">" : "]");
    }
}
