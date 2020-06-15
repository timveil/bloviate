/*
 * Copyright 2020 Tim Veil
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.bloviate.gen;

import org.apache.commons.lang3.RandomUtils;

import java.sql.ResultSet;
import java.sql.SQLException;

public class CharacterGenerator extends AbstractDataGenerator<Character> {

    @Override
    public Character generate() {
        return (char) (RandomUtils.nextInt(0, 26) + 'a');
    }

    @Override
    public Character get(ResultSet resultSet, int columnIndex) throws SQLException {
        String character = resultSet.getString(columnIndex);

        if (character != null) {

            if (character.length() > 1) {
                throw  new IllegalArgumentException("character length is greater than 1");
            }

            return character.charAt(0);
        }

        return null;
    }

    public static class Builder {
        public CharacterGenerator build() {
            return new CharacterGenerator(this);
        }
    }

    private CharacterGenerator(Builder builder) {

    }
}
