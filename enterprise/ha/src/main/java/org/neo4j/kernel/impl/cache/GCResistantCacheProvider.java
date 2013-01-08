/**
 * Copyright (c) 2002-2013 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.cache;

import static org.neo4j.graphdb.factory.GraphDatabaseSetting.FloatSetting;
import static org.neo4j.graphdb.factory.GraphDatabaseSetting.NumberOfBytesSetting;
import static org.neo4j.graphdb.factory.GraphDatabaseSetting.TimeSpanSetting;

import org.neo4j.graphdb.factory.Default;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.helpers.Service;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.core.NodeImpl;
import org.neo4j.kernel.impl.core.RelationshipImpl;
import org.neo4j.kernel.impl.util.StringLogger;

@Service.Implementation( CacheProvider.class )
public class GCResistantCacheProvider extends CacheProvider
{
    public static final String NAME = "gcr";

    public static class Configuration
    {
        public static GraphDatabaseSetting<Long> node_cache_size = new GCRMemoryUsageSetting("node_cache_size");
        public static GraphDatabaseSetting<Long> relationship_cache_size = new GCRMemoryUsageSetting("relationship_cache_size");

        @Default( "1.0" )
        public static GraphDatabaseSetting<Float> node_cache_array_fraction = new FloatSetting( "node_cache_array_fraction", "Must be a valid floating point number.", 1.0f, 10.0f );

        @Default( "1.0" )
        public static GraphDatabaseSetting<Float> relationship_cache_array_fraction = new FloatSetting( "relationship_cache_array_fraction", "Must be a valid floating point number.", 1.0f, 10.0f );

        @Default( "60s" )
        public static GraphDatabaseSetting<Long> log_interval = new TimeSpanSetting( "gcr_cache_min_log_interval" );

        private static final class GCRMemoryUsageSetting extends NumberOfBytesSetting implements org.neo4j.graphdb.factory.GraphDatabaseSetting.DefaultValue
        {

            public GCRMemoryUsageSetting(String name) {
                super(name);
            }

            @Override
            public String getDefaultValue() {
                long available = Runtime.getRuntime().maxMemory();
                long defaultMem = ( available / 4);
                return ""+defaultMem;
            }

        }
    }

    public GCResistantCacheProvider()
    {
        super( NAME, "GC resistant cache" );
    }

    @Override
    public Cache<NodeImpl> newNodeCache( StringLogger logger, Config config )
    {
        long node = config.get( Configuration.node_cache_size );
        long rel = config.get( Configuration.relationship_cache_size );
        checkMemToUse( logger, node, rel, Runtime.getRuntime().maxMemory() );
        return new GCResistantCache<NodeImpl>( node, config.get( Configuration.node_cache_array_fraction ), config.get( Configuration.log_interval ),
                NODE_CACHE_NAME, logger );
    }

    @Override
    public Cache<RelationshipImpl> newRelationshipCache( StringLogger logger, Config config )
    {
        long node = config.get( Configuration.node_cache_size );
        long rel = config.get( Configuration.relationship_cache_size );
        checkMemToUse( logger, node, rel, Runtime.getRuntime().maxMemory() );
        return new GCResistantCache<RelationshipImpl>( rel, config.get( Configuration.relationship_cache_array_fraction ), config.get( Configuration.log_interval ),
                RELATIONSHIP_CACHE_NAME, logger );
    }

    // TODO: Move into validation method of config setting?
    @SuppressWarnings( "boxing" )
    private void checkMemToUse( StringLogger logger, long node, long rel, long available )
    {
        long advicedMax = available / 2;
        long total = 0;
        node = Math.max( GCResistantCache.MIN_SIZE, node );
        total += node;
        rel = Math.max( GCResistantCache.MIN_SIZE, rel );
        total += rel;
        if ( total > available )
        {
            throw new IllegalArgumentException(
                    String.format( "Configured cache memory limits (node=%s, relationship=%s, " +
                            "total=%s) exceeds available heap space (%s)",
                            node, rel, total, available ) );
        }
        if ( total > advicedMax )
        {
            logger.logMessage( String.format( "Configured cache memory limits(node=%s, relationship=%s, " +
                    "total=%s) exceeds recommended limit (%s)",
                    node, rel, total, advicedMax ) );
        }
    }

    @Override
    public Class getSettingsClass()
    {
        return Configuration.class;
    }
}
