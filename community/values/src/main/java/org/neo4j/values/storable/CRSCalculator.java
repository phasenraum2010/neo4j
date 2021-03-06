/*
 * Copyright (c) 2002-2018 "Neo Technology,"
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
package org.neo4j.values.storable;

import org.neo4j.helpers.collection.Pair;

import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.pow;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

public abstract class CRSCalculator
{
    public abstract double distance( PointValue p1, PointValue p2 );

    public abstract Pair<PointValue,PointValue> boundingBox( PointValue center, double distance );

    protected static double pythagoras( double[] a, double[] b )
    {
        double sqrSum = 0.0;
        for ( int i = 0; i < a.length; i++ )
        {
            double diff = a[i] - b[i];
            sqrSum += diff * diff;
        }
        return sqrt( sqrSum );
    }

    public static class CartesianCalculator extends CRSCalculator
    {
        int dimension;

        CartesianCalculator( int dimension )
        {
            this.dimension = dimension;
        }

        @Override
        public double distance( PointValue p1, PointValue p2 )
        {
            assert p1.getCoordinateReferenceSystem().getDimension() == dimension;
            assert p2.getCoordinateReferenceSystem().getDimension() == dimension;
            return pythagoras( p1.coordinate(), p2.coordinate() );
        }

        @Override
        public Pair<PointValue,PointValue> boundingBox( PointValue center, double distance )
        {
            assert center.getCoordinateReferenceSystem().getDimension() == dimension;
            double[] coordinates = center.coordinate();
            double[] min = new double[dimension];
            double[] max = new double[dimension];
            for ( int i = 0; i < dimension; i++ )
            {
                min[i] = coordinates[i] - distance;
                max[i] = coordinates[i] + distance;
            }
            CoordinateReferenceSystem crs = center.getCoordinateReferenceSystem();
            return Pair.of( Values.pointValue( crs, min ), Values.pointValue( crs, max ) );
        }
    }

    public static class GeographicCalculator extends CRSCalculator
    {
        public static final double EARTH_RADIUS_METERS = 6378140.0;
        private static final double EXTENSION_FACTOR = 1.0001;
        int dimension;

        GeographicCalculator( int dimension )
        {
            this.dimension = dimension;
        }

        @Override
        public double distance( PointValue p1, PointValue p2 )
        {
            assert p1.getCoordinateReferenceSystem().getDimension() == dimension;
            assert p2.getCoordinateReferenceSystem().getDimension() == dimension;
            double[] c1Coord = p1.coordinate();
            double[] c2Coord = p2.coordinate();
            double[] c1 = new double[]{toRadians( c1Coord[0] ), toRadians( c1Coord[1] )};
            double[] c2 = new double[]{toRadians( c2Coord[0] ), toRadians( c2Coord[1] )};
            double dx = c2[0] - c1[0];
            double dy = c2[1] - c1[1];
            double alpha = pow( sin( dy / 2 ), 2.0 ) + cos( c1[1] ) * cos( c2[1] ) * pow( sin( dx / 2.0 ), 2.0 );
            double greatCircleDistance = 2.0 * atan2( sqrt( alpha ), sqrt( 1 - alpha ) );
            double distance2D = EARTH_RADIUS_METERS * greatCircleDistance;
            if ( dimension > 2 )
            {
                double[] a = new double[dimension - 1];
                double[] b = new double[dimension - 1];
                a[0] = distance2D;
                b[0] = 0.0;
                for ( int i = 1; i < dimension - 1; i++ )
                {
                    a[i] = 0.0;
                    b[i] = c1Coord[i + 1] - c2Coord[i + 1];
                }
                return pythagoras( a, b );
            }
            else
            {
                return distance2D;
            }
        }

        @Override
        // http://janmatuschek.de/LatitudeLongitudeBoundingCoordinates
        // But calculating in degrees instead of radians to avoid rounding errors
        public Pair<PointValue,PointValue> boundingBox( PointValue center, double distance )
        {
            if ( distance == 0.0 )
            {
                return Pair.of( center, center );
            }

            // Extend the distance slightly to assure that all relevant points lies inside the bounding box,
            // with rounding errors taken into account
            double extended_distance = distance * EXTENSION_FACTOR;

            CoordinateReferenceSystem crs = center.getCoordinateReferenceSystem();
            double lat = center.coordinate()[1];
            double lon = center.coordinate()[0];

            double r = extended_distance / EARTH_RADIUS_METERS;

            double lat_min = lat - toDegrees( r );
            double lat_max = lat + toDegrees( r );

            // If your query circle includes one of the poles
            if ( lat_max >= 90 )
            {
                return boundingBoxOf( -180, 180, lat_min, 90, center, distance );
            }
            else if ( lat_min <= -90 )
            {
                return boundingBoxOf( -180, 180, -90, lat_max, center, distance );
            }
            else
            {
                double delta_lon = toDegrees( asin( sin( r ) / cos( toRadians( lat ) ) ) );
                double lon_min = lon - delta_lon;
                double lon_max = lon + delta_lon;

                // If you query circle wraps around the dateline
                // Large rectangle covering all longitudes
                // TODO implement two rectangle solution instead
                if ( lon_min < -180 || lon_max > 180 )
                {
                    return boundingBoxOf( -180, 180, lat_min, lat_max, center, distance );
                }
                else
                {
                    return boundingBoxOf( lon_min, lon_max, lat_min, lat_max, center, distance );
                }
            }
        }

        private Pair<PointValue,PointValue> boundingBoxOf( double minLon, double maxLon, double minLat, double maxLat, PointValue center, double distance )
        {
            CoordinateReferenceSystem crs = center.getCoordinateReferenceSystem();
            int dimension = center.getCoordinateReferenceSystem().getDimension();
            double[] min = new double[dimension];
            double[] max = new double[dimension];
            min[0] = minLon;
            min[1] = minLat;
            max[0] = maxLon;
            max[1] = maxLat;
            if ( dimension > 2 )
            {
                double[] coordinates = center.coordinate();
                for ( int i = 2; i < dimension; i++ )
                {
                    min[i] = coordinates[i] - distance;
                    max[i] = coordinates[i] + distance;
                }
            }
            return Pair.of( Values.pointValue( crs, min ), Values.pointValue( crs, max ) );
        }
    }
}
