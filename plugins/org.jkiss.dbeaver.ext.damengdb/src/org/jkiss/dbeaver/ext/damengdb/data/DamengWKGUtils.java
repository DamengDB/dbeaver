/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
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
package org.jkiss.dbeaver.ext.damengdb.data;

import org.cugos.wkg.*;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.decomposition.lu.LUDecompositionAlt_DDRM;
import org.ejml.dense.row.linsol.lu.LinearSolverLu_DDRM;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.gis.DBGeometry;
import org.jkiss.utils.CommonUtils;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * WKG geometry utils
 */
public class DamengWKGUtils {

    /**
     * Parses WKT (Well-known text) or its extension EWKT (Extended well-known text)
     *
     * @return parsed geometry
     * @throws DBCException on parse error
     */
    @NotNull
    public static DBGeometry parseWKT(@NotNull String wkt) throws DBCException {
        int srid = 0;

        if (wkt.startsWith("SRID=") && wkt.indexOf(';') > 5) {
            final int index = wkt.indexOf(';');
            srid = CommonUtils.toInt(wkt.substring(5, index));
            wkt = wkt.substring(index + 1);
        }

        final DBGeometry geometry;

        try {
            geometry = new DBGeometry(new WKTReader().read(wkt));
        } catch (Exception e) {
            throw new DBCException("Error parsing geometry value from string", e);
        }

        if (srid != 0) {
            geometry.setSRID(srid);
        }

        return geometry;
    }

    public static DBGeometry parseWKB(String hexString) throws DBCException {
        org.cugos.wkg.Geometry wkgGeometry = new WKBReader().read(hexString);
        if (wkgGeometry != null) {
            final int srid = CommonUtils.toInt(wkgGeometry.getSrid());
            // Nullify geometry's SRID so it's not included in its toString
            // representation
            wkgGeometry.setSrid(null);
            return new DBGeometry(wkgGeometry, srid);
        }
        throw new DBCException("Invalid geometry object");
    }

    public static boolean isCurve(@Nullable Object value) {
        return value instanceof CircularString || value instanceof CompoundCurve || value instanceof CurvePolygon
            || value instanceof MultiCurve || value instanceof MultiSurface;
    }

    @NotNull
    public static Object linearize(@NotNull Geometry value) {
        // This value results in 32 segments per quadrant, the default tolerance
        // for ST_CurveToLine
        return linearize(value, 0.001);
    }

    @NotNull
    public static Geometry linearize(@NotNull Geometry value, double tolerance) {
        if (value instanceof CircularString) {
            return convertCircularString((CircularString) value, tolerance);
        } else if (value instanceof CompoundCurve) {
            return convertCompoundCurve((CompoundCurve) value, tolerance);
        } else if (value instanceof CurvePolygon) {
            return convertCurvePolygon((CurvePolygon) value, tolerance);
        } else if (value instanceof MultiCurve) {
            return convertMultiCurve((MultiCurve) value, tolerance);
        } else if (value instanceof MultiSurface) {
            return convertMultiSurface((MultiSurface) value, tolerance);
        } else {
            return value;
        }
    }

    @NotNull
    private static LineString convertCircularString(@NotNull CircularString value, double tolerance) {
        final List<Coordinate> input = value.getCoordinates();
        final List<Coordinate> output = new ArrayList<>();

        for (int i = 2; i < input.size(); i += 2) {
            final CircularArc arc = new CircularArc(input.get(i - 2), input.get(i - 1), input.get(i));

            output.addAll(arc.linearize(tolerance));
        }

        return new LineString(output, value.getDimension(), value.getSrid());
    }

    @NotNull
    private static LineString convertCompoundCurve(@NotNull CompoundCurve value, double tolerance) {
        final List<Coordinate> coordinates = value.getCurves().stream().map(x -> linearize(x, tolerance))
            .flatMap(x -> x.getCoordinates().stream()).collect(Collectors.toList());

        return new LineString(coordinates, value.getDimension(), value.getSrid());
    }

    @NotNull
    private static Polygon convertCurvePolygon(@NotNull CurvePolygon value, double tolerance) {
        final LinearRing outerLinearRing = Stream.of(value.getOuterCurve()).map(x -> linearize(x, tolerance))
            .map(x -> new LinearRing(x.getCoordinates(), x.getDimension(), x.getSrid())).findAny().get();

        final List<LinearRing> innerLinearRings = value.getInnerCurves().stream().map(x -> linearize(x, tolerance))
            .map(x -> new LinearRing(x.getCoordinates(), x.getDimension(), x.getSrid()))
            .collect(Collectors.toList());

        return new Polygon(outerLinearRing, innerLinearRings, value.getDimension(), value.getSrid());
    }

    @NotNull
    private static MultiLineString convertMultiCurve(@NotNull MultiCurve value, double tolerance) {
        final List<LineString> strings = value.getCurves().stream().map(x -> (LineString) linearize(x, tolerance))
            .collect(Collectors.toList());

        return new MultiLineString(strings, value.getDimension(), value.getSrid());
    }

    @NotNull
    private static MultiPolygon convertMultiSurface(@NotNull MultiSurface value, double tolerance) {
        final List<Polygon> polygons = value.getSurfaces().stream().map(x -> (Polygon) linearize(x, tolerance))
            .collect(Collectors.toList());

        return new MultiPolygon(polygons, value.getDimension(), value.getSrid());
    }

    public static org.locationtech.jts.geom.Geometry getJtsGeometry(@Nullable Object object) {
        if (object instanceof org.locationtech.jts.geom.Geometry) {
            return (org.locationtech.jts.geom.Geometry) object;
        } else if (object instanceof org.cugos.wkg.Geometry) {
            try {
                return new WKTReader().read(object.toString());
            } catch (ParseException e) {
            }
        }

        return null;
    }

    public static boolean dbeaverIs232() {
        try {
            Class.forName("org.jkiss.dbeaver.model.gis.CircularArc");
            return true;
        } catch (ClassNotFoundException e) {
        }

        return false;
    }

    static class CircularArc {
        private static final int DEFAULT_SEGMENTS_QUADRANT = 12;

        private static final int MAXIMUM_SEGMENTS_QUADRANT = 10000;

        private static final double HALF_PI = Math.PI / 2;

        private static final double DOUBLE_PI = Math.PI * 2;

        private final Coordinate[] controlPoints;

        private double radius;

        private double centerX;

        private double centerY;

        public CircularArc(@NotNull Coordinate[] controlPoints) {
            this.radius = Double.NaN;

            if (controlPoints.length == 3) {
                this.controlPoints = controlPoints;
            } else {
                throw new IllegalArgumentException(
                    "Invalid control point array, it must be made of of 3 control points, start, mid and end");
            }
        }

        public CircularArc(@NotNull Coordinate start, @NotNull Coordinate mid, @NotNull Coordinate end) {
            this(new Coordinate[] {start, mid, end});
        }

        private static boolean equals(double a, double b) {
            return Math.abs(a - b) < 1.0E-12D;
        }

        @NotNull
        public List<Coordinate> linearize(double tolerance) {
            initializeCenterRadius();

            if (radius != Double.POSITIVE_INFINITY && radius != 0.0D) {
                return linearize(tolerance, new ArrayList<>());
            } else {
                return List.of(controlPoints);
            }
        }

        private Coordinate createCoordinate(double x, double y) {
            Dimension dimension = controlPoints[0].getDimension();
            if (dimension == Dimension.TwoMeasured) {
                return Coordinate.create2DM(x, y, controlPoints[0].getM());
            } else if (dimension == Dimension.Three) {
                return Coordinate.create3D(x, y, controlPoints[0].getZ());
            } else if (dimension == Dimension.ThreeMeasured) {
                return Coordinate.create3DM(x, y, controlPoints[0].getZ(), controlPoints[0].getM());
            } else {
                return Coordinate.create2D(x, y);
            }
        }

        @NotNull
        private List<Coordinate> linearize(double tolerance, @NotNull List<Coordinate> array) {
            initializeCenterRadius();

            double sx = controlPoints[0].getX();
            double sy = controlPoints[0].getY();
            double mx = controlPoints[1].getX();
            double my = controlPoints[1].getY();
            double ex = controlPoints[2].getX();
            double ey = controlPoints[2].getY();
            double sa = Math.atan2(sy - centerY, sx - centerX);
            double ma = Math.atan2(my - centerY, mx - centerX);
            double ea = Math.atan2(ey - centerY, ex - centerX);
            boolean clockwise = sa > ma && ma > ea || sa > ma && sa < ea || ma > ea && sa < ea;

            if (clockwise) {
                double tx = sx;
                double ty = sy;
                double ta = sa;

                sx = ex;
                ex = tx;
                sy = ey;
                ey = ty;
                sa = ea;
                ea = ta;
            }

            if (ma < sa) {
                ma += DOUBLE_PI;
                ea += DOUBLE_PI;
            } else if (ea < sa) {
                ea += DOUBLE_PI;
            }

            double step = HALF_PI / computeSegmentsPerQuadrant(tolerance);
            double angle = (Math.floor(sa / step) + 1.0D) * step;

            if (angle <= ea) {
                int start = array.size();

                array.add(createCoordinate(sx, sy));

                if (angle > ma) {
                    array.add(createCoordinate(mx, my));

                    if (equals(angle, ma)) {
                        angle += step;
                    }
                }

                for (double next, end = ea - 1.0E-12D; angle < end; angle = next) {
                    final double cx = centerX + radius * Math.cos(angle);
                    final double cy = centerY + radius * Math.sin(angle);

                    array.add(createCoordinate(cx, cy));
                    next = angle + step;

                    if (angle < ma && next > ma && !equals(angle, ma) && !equals(next, ma)) {
                        array.add(createCoordinate(mx, my));
                    }
                }

                array.add(createCoordinate(ex, ey));

                if (clockwise) {
                    Collections.reverse(array.subList(start, array.size()));
                }
            } else {
                array.addAll(List.of(controlPoints));
            }

            return array;
        }

        private int computeSegmentsPerQuadrant(double tolerance) {
            if (tolerance < 0.0D) {
                throw new IllegalArgumentException("The tolerance must be a positive number, "
                    + "zero to use the default number of segments per quadrant (" + DEFAULT_SEGMENTS_QUADRANT
                    + "), " + "or Double.MAX_VALUE to use the max number of segments per quadrant ("
                    + MAXIMUM_SEGMENTS_QUADRANT + ")");
            }

            int segmentsPerQuadrant;
            double chordDistance;

            if (tolerance == 0.0D) {
                segmentsPerQuadrant = DEFAULT_SEGMENTS_QUADRANT;
            } else if (tolerance == Double.MAX_VALUE) {
                segmentsPerQuadrant = MAXIMUM_SEGMENTS_QUADRANT;
            } else {
                segmentsPerQuadrant = 2;
                chordDistance = computeChordCircleDistance(segmentsPerQuadrant);

                if (chordDistance >= tolerance) {
                    while (chordDistance > tolerance && segmentsPerQuadrant < MAXIMUM_SEGMENTS_QUADRANT) {
                        segmentsPerQuadrant *= 2;
                        chordDistance = computeChordCircleDistance(segmentsPerQuadrant);
                    }
                } else {
                    while (chordDistance < tolerance && segmentsPerQuadrant > 1) {
                        segmentsPerQuadrant /= 2;
                        chordDistance = computeChordCircleDistance(segmentsPerQuadrant);
                    }

                    if (chordDistance > tolerance) {
                        segmentsPerQuadrant *= 2;
                    }
                }
            }

            return segmentsPerQuadrant;
        }

        private double computeChordCircleDistance(int segmentsPerQuadrant) {
            double halfChordLength = radius * Math.sin(HALF_PI / segmentsPerQuadrant);
            double apothem = Math.sqrt(radius * radius - halfChordLength * halfChordLength);
            return radius - apothem;
        }

        private void initializeCenterRadius() {
            if (!Double.isNaN(radius)) {
                return;
            }

            double sx = controlPoints[0].getX();
            double sy = controlPoints[0].getY();
            double mx = controlPoints[1].getX();
            double my = controlPoints[1].getY();
            double ex = controlPoints[2].getX();
            double ey = controlPoints[2].getY();
            double dx12;
            double dy12;
            double rs;
            double dy13;
            double dx23;
            double rm;
            double sqs1;
            double sqs2;
            double re;

            if (equals(sx, ex) && equals(sy, ey)) {
                centerX = sx + (mx - sx) / 2.0D;
                centerY = sy + (my - sy) / 2.0D;
            } else {
                dx12 = sx - mx;
                dy12 = sy - my;
                rs = sx - ex;
                dy13 = sy - ey;
                dx23 = mx - ex;
                rm = my - ey;
                sqs1 = dx12 * dx12 + dy12 * dy12;
                sqs2 = rs * rs + dy13 * dy13;
                re = dx23 * dx23 + rm * rm;
                DMatrixRMaj b;
                double sqs;
                DMatrixRMaj A;
                if (sqs1 <= re && sqs2 <= re) {
                    A = new DMatrixRMaj(2, 2, true, dx12, dy12, rs, dy13);
                    b = new DMatrixRMaj(2, 1, true, 0.5D * (dx12 * (sx + mx) + dy12 * (sy + my)),
                        0.5D * (rs * (sx + ex) + dy13 * (sy + ey)));
                    sqs = sqs1 + sqs2;
                } else if (sqs1 <= sqs2 && re <= sqs2) {
                    A = new DMatrixRMaj(2, 2, true, dx12, dy12, dx23, rm);
                    b = new DMatrixRMaj(2, 1, true, 0.5D * (dx12 * (sx + mx) + dy12 * (sy + my)),
                        0.5D * (dx23 * (mx + ex) + rm * (my + ey)));
                    sqs = sqs1 + re;
                } else {
                    A = new DMatrixRMaj(2, 2, true, rs, dy13, dx23, rm);
                    b = new DMatrixRMaj(2, 1, true, 0.5D * (rs * (sx + ex) + dy13 * (sy + ey)),
                        0.5D * (dx23 * (mx + ex) + rm * (my + ey)));
                    sqs = sqs2 + re;
                }

                LUDecompositionAlt_DDRM lu = new LUDecompositionAlt_DDRM();
                LinearSolverLu_DDRM solver = new LinearSolverLu_DDRM(lu);
                if (!solver.setA(A)) {
                    radius = Double.POSITIVE_INFINITY;
                    return;
                }

                double R = 2.0D * Math.abs(lu.computeDeterminant().getReal()) / sqs;
                double k = (1.0D + Math.sqrt(1.0D - R * R)) / R;
                if (k > 20000.0D) {
                    radius = Double.POSITIVE_INFINITY;
                    return;
                }

                DMatrixRMaj x = new DMatrixRMaj(2, 1);
                solver.solve(b, x);
                centerX = x.get(0);
                centerY = x.get(1);
            }

            rs = Math.sqrt(Math.pow(centerX - sx, 2) + Math.pow(centerY - sy, 2));
            rm = Math.sqrt(Math.pow(centerX - mx, 2) + Math.pow(centerY - my, 2));
            re = Math.sqrt(Math.pow(centerX - ex, 2) + Math.pow(centerY - ey, 2));
            radius = Math.min(Math.max(rs, rm), re);
        }
    }
    
}
