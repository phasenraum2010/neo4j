/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api.store;

import java.util.function.Consumer;

import org.neo4j.kernel.impl.locking.Lock;
import org.neo4j.kernel.impl.store.RecordCursors;

public class StoreSinglePropertyCursor extends StoreAbstractPropertyCursor
{
    private final Consumer<StoreSinglePropertyCursor> instanceCache;

    StoreSinglePropertyCursor( RecordCursors cursors, Consumer<StoreSinglePropertyCursor> instanceCache )
    {
        super( cursors  );
        this.instanceCache = instanceCache;
    }

    public StoreSinglePropertyCursor init( int propertyKeyId, long firstPropertyId, Lock lock )
    {
        initialize( key -> key == propertyKeyId, firstPropertyId, lock );
        return this;
    }

    @Override
    protected boolean loop()
    {
        return !fetched;
    }

    @Override
    protected void doClose()
    {
        instanceCache.accept( this );
    }
}
