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

import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;

import org.geotools.geometry.jts.CircularString;
import org.geotools.geometry.jts.CompoundCurve;
import org.geotools.geometry.jts.CurvePolygon;
import org.geotools.geometry.jts.MultiCurve;
import org.geotools.geometry.jts.MultiSurface;

import org.jkiss.code.NotNull;
import org.jkiss.dbeaver.ext.damengdb.model.DamengExecutionContext;
import org.jkiss.dbeaver.model.data.DBDAttributeBinding;
import org.jkiss.dbeaver.model.data.DBDDisplayFormat;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCPreparedStatement;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCSession;
import org.jkiss.dbeaver.model.gis.DBGeometry;
import org.jkiss.dbeaver.model.gis.GisAttribute;
import org.jkiss.dbeaver.model.impl.jdbc.data.handlers.JDBCAbstractValueHandler;

import org.jkiss.dbeaver.model.struct.DBSTypedObject;
import org.jkiss.utils.CommonUtils;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;

import com.dameng.geotools.util.dm.ByteEndian;
import com.dameng.geotools.util.dm.DmGeo2Util;

/**
 * Dameng geometry handler
 */
public class DamengGeometryValueHandler extends JDBCAbstractValueHandler {
	
    public static final int CURVEPOLYGON_TYPE = 10;

    public static final int POLYHEDRALSURFACE_TYPE = 13;

    public static final int TRIANGLE_TYPE = 14;

    public static final int TIN_TYPE = 15;

    public static final DamengGeometryValueHandler INSTANCE = new DamengGeometryValueHandler();

    protected static Struct dmStruct = null;

    protected static String bytesToHexString(byte[] bts) {
        StringBuilder stringBuilder = new StringBuilder("");
        for (int i = 0; i < bts.length; i++) {
            int temp = bts[i] & 0xFF;
            String htemp = Integer.toHexString(temp);
            if (htemp.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(htemp);
        }
        return stringBuilder.toString();
    }

    protected static Object dbGeometryFromTopologyStruct(DBCSession session, Struct geoStruct)
        throws SQLException, DBCException {
        if (geoStruct == null) {
            return null;
        }

        Object[] geoObj = geoStruct.getAttributes();
        int topologyID = (int) geoObj[0];
        int layerID = (int) geoObj[1];
        int topogeoID = (int) geoObj[2];
        int elementType = (int) geoObj[3];

        DBGeometry finGeometry = null;

        DBCExecutionContext executionContext = session.getExecutionContext();
        Connection conn = ((DamengExecutionContext) executionContext).getConnection(null);
        String sql = "select systopology.dmtopology.geometry(SYSTOPOLOGY.TOPOGEOMETRY(?,?,?,?));";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setInt(1, topologyID);
        ps.setInt(2, layerID);
        ps.setInt(3, topogeoID);
        ps.setInt(4, elementType);
        ResultSet resultSet = ps.executeQuery();
        while (resultSet.next()) {
            Struct geo2Struct = (Struct) resultSet.getObject(1);
            finGeometry = (DBGeometry) dbGeometryFromGeo2Struct(geo2Struct);
        }
        resultSet.close();
        ps.close();

        return finGeometry;
    }

    public static DamengDBGeometry parseWKB(String hexString, int gserType) throws DBCException {
        org.cugos.wkg.Geometry wkgGeometry = new org.cugos.wkg.WKBReader().read(hexString);
        if (wkgGeometry != null) {
            final int srid = CommonUtils.toInt(wkgGeometry.getSrid());
            // Nullify geometry's SRID so it's not included in its toString
            // representation
            wkgGeometry.setSrid(null);
            String text = wkgGeometry.toString();
            if (DamengWKGUtils.isCurve(wkgGeometry)) {
                wkgGeometry = (org.cugos.wkg.Geometry) DamengWKGUtils.linearize(wkgGeometry);
            }
            DBGeometry dbGeometry = new DBGeometry(wkgGeometry, srid);
            return new DamengDBGeometry(dbGeometry, text, gserType);
        }
        throw new DBCException("Invalid geometry object");
    }

    @NotNull
    public static DBGeometry parseWKT(DBCSession session, @NotNull String wkt, String type) throws DBCException {
        int srid = 0;
        DBGeometry finGeometry = null;

        if (wkt.startsWith("SRID=") && wkt.indexOf(';') > 5) {
            final int index = wkt.indexOf(';');
            srid = CommonUtils.toInt(wkt.substring(5, index));
            wkt = wkt.substring(index + 1, wkt.length());
        }

        try {
            DBCExecutionContext executionContext = session.getExecutionContext();
            Connection conn = ((DamengExecutionContext) executionContext).getConnection(null);
            String sql = "select dmgeo.st_geomfromtext(?, ?) from dual";
            if (type.startsWith("SYSGEO2")) {
                sql = "select dmgeo2.st_geomfromtext(?, ?) from dual";
            }

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setObject(1, wkt);
            ps.setObject(2, srid);

            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                Struct geoStruct = (Struct) resultSet.getObject(1);
                dmStruct = geoStruct;

                if (type.startsWith("SYSGEO.")) {
                    finGeometry = (DBGeometry) dbGeometryFromGeo1Struct(geoStruct);
                } else {
                    finGeometry = (DBGeometry) dbGeometryFromGeo2Struct(geoStruct);
                }
                if (srid != 0) {
                    finGeometry.setSRID(srid);
                }

                resultSet.close();
                ps.close();

                return finGeometry;
            }
        } catch (Exception e) {
            throw new DBCException("Error parsing geometry value from string", e);
        }

        return finGeometry;
    }

    protected static Object dbGeometryFromGeo2Struct(Struct geoStruct) throws SQLException, DBCException {
        if (geoStruct == null) {
            return null;
        }

        Object[] geoObj = geoStruct.getAttributes();
        Blob gSerBlob = (Blob) geoObj[0];
        if (gSerBlob == null) {
            return null;
        }
        int len = (int) gSerBlob.length();
        byte[] gserBytes = gSerBlob.getBytes(1, len);
        byte[] ewkb = null;
        try {
            ewkb = DmGeo2Util.gserialized(gserBytes).toEwkb(ByteEndian.NDR);

            // WKBReader reader = new WKBReader();
            org.geotools.geometry.jts.WKBReader reader = new org.geotools.geometry.jts.WKBReader();
            Geometry geometry = reader.read(ewkb);
            if (geometry instanceof CircularString) {
                CircularString t = (CircularString) geometry;
                geometry = t.linearize();
            } else if (geometry instanceof CompoundCurve) {
                CompoundCurve t = (CompoundCurve) geometry;
                geometry = t.linearize();
            } else if (geometry instanceof CurvePolygon) {
                CurvePolygon t = (CurvePolygon) geometry;
                geometry = t.linearize();
            } else if (geometry instanceof MultiCurve) {
                MultiCurve t = (MultiCurve) geometry;
                geometry = t.linearize();
            } else if (geometry instanceof MultiSurface) {
                MultiSurface t = (MultiSurface) geometry;
                geometry = t.linearize();
            } else {
                return new DamengDBGeometry(new DBGeometry(geometry), null, 0);
            }

            org.cugos.wkg.Geometry wkgGeometry = new org.cugos.wkg.WKBReader().read(ewkb);
            wkgGeometry.setSrid(null);
            String text = wkgGeometry.toString();
            return new DamengDBGeometry(new DBGeometry(geometry), text, 0);
        } catch (Exception e) {
            // Try to parse as WKG
            int gserType = 0;
            try {
                gserType = DmGeo2Util.gserialized(gserBytes).getType();
                int srid = DmGeo2Util.gserialized(gserBytes).getSrid();
                if (gserType == CURVEPOLYGON_TYPE && DmGeo2Util.gserialized(gserBytes).isEmpty() == true) {
                    DBGeometry tempDbGeometry = new DBGeometry(org.cugos.wkg.CurvePolygon.createEmpty(), srid);
                    String text = tempDbGeometry.toString();
                    return new DamengDBGeometry(tempDbGeometry, text, gserType);
                }
                if (gserType == POLYHEDRALSURFACE_TYPE || gserType == TRIANGLE_TYPE || gserType == TIN_TYPE) {
                    gserBytes = DmGeo2Util.gserialized(gserBytes).transform();
                    ewkb = DmGeo2Util.gserialized(gserBytes).toEwkb(ByteEndian.NDR);
                }

                String ewkbHexString = bytesToHexString(ewkb);
                return parseWKB(ewkbHexString, gserType);
            } catch (Exception ex) {
                e = ex;

                try {
                    WKBReader reader = new WKBReader();
                    Geometry geometry = reader.read(ewkb);
                    return new DamengDBGeometry(new DBGeometry(geometry), null, gserType);
                } catch (ParseException e1) {
                    e = e1;
                }
            }

            throw new DBCException("fetch column value failed", e);
        }
    }

    protected static Object dbGeometryFromGeo1Struct(Struct geoStruct) throws SQLException, DBCException {
        if (geoStruct == null) {
            return null;
        }

        Object[] attrs = geoStruct.getAttributes();
        int srid = (Integer) attrs[0];
        Blob gSerBlob = (Blob) attrs[1];

        if (gSerBlob == null) {
            return null;
        }
        int len = (int) gSerBlob.length();
        byte[] ewkb = gSerBlob.getBytes(1, len);

        try {
            org.geotools.geometry.jts.WKBReader reader = new org.geotools.geometry.jts.WKBReader();
            Geometry geometry = reader.read(ewkb);
            geometry.setSRID(srid);
            return new DamengDBGeometry(new DBGeometry(geometry), null, 0);
        } catch (Exception e) {
            // Try to parse as WKG
            int gserType = 0;
            try {

                String ewkbHexString = bytesToHexString(ewkb);
                return parseWKB(ewkbHexString, gserType);
            } catch (Exception ex) {
                e = ex;

                try {
                    WKBReader reader = new WKBReader();
                    Geometry geometry = reader.read(ewkb);
                    return new DamengDBGeometry(new DBGeometry(geometry), null, 0);
                } catch (ParseException e1) {
                    e = e1;
                }
            }

            throw new DBCException("fetch column value failed", e);
        }
    }

    @Override
    protected Object fetchColumnValue(DBCSession session, JDBCResultSet resultSet, DBSTypedObject type, int index)
        throws DBCException, SQLException {
        Struct geostruct = (Struct) resultSet.getObject(index);
        if (type.getTypeName().startsWith("SYSGEO2")) {
            return dbGeometryFromGeo2Struct(geostruct);
        } else if (type.getTypeName().startsWith("SYSTOPOLOGY")) {
            return dbGeometryFromTopologyStruct(session, geostruct);
        } else {
            return dbGeometryFromGeo1Struct(geostruct);
        }

    }

    @Override
    protected void bindParameter(JDBCSession session, JDBCPreparedStatement statement, DBSTypedObject paramType,
                                 int paramIndex, Object value) throws DBCException, SQLException {
        int valueSRID = 0;
        if (paramType instanceof DBDAttributeBinding) {
            paramType = ((DBDAttributeBinding) paramType).getAttribute();
        }
        if (value instanceof DBGeometry) {
            valueSRID = ((DBGeometry) value).getSRID();
        }
        if (valueSRID == 0 && paramType instanceof GisAttribute) {
            valueSRID = ((GisAttribute) paramType).getAttributeGeometrySRID(session.getProgressMonitor());
        }
        if (value == null) {
            statement.setNull(paramIndex, paramType.getTypeID());
        } else {
            if (((DBGeometry) value).getSRID() == 0) {
                ((DBGeometry) value).setSRID(valueSRID);
            }
            statement.setObject(paramIndex, dmStruct);
        }
    }

    @NotNull
    @Override
    public Class<?> getValueObjectType(@NotNull DBSTypedObject attribute) {
        return DBGeometry.class;
    }

    @Override
    public Object getValueFromObject(@NotNull DBCSession session, @NotNull DBSTypedObject type, Object object,
                                     boolean copy, boolean validateValue) throws DBCException {
        if (object == null) {
            return new DBGeometry();
        } else if (object instanceof DBGeometry) {
            if (copy) {
                return ((DBGeometry) object).copy();
            } else {
                return object;
            }
        } else if (object instanceof Geometry) {
            return new DBGeometry((Geometry) object);
        } else if (object instanceof String) {
            return makeGeometryFromWKT(session, (String) object, type.getTypeName());
        } else if (type.toString().equalsIgnoreCase("SYSTOPOLOGY.TOPOGEOMETRY.ID")) {
            if (object instanceof Object[]) {
                Object[] geoObj = (Object[]) object;
                int topologyID = (int) geoObj[0];
                int layerID = (int) geoObj[1];
                int topogeoID = (int) geoObj[2];
                int elementType = (int) geoObj[3];

                String text = geoObj[2].toString();
                int[] topology = {topologyID, layerID, topogeoID, elementType};
                return new DamengDBGeometry(text, session, topology);
            } else {
                return object;
            }
        } else {
            return makeGeometryFromWKT(session, object.toString(), type.getTypeName());
        }
    }

    @NotNull
    @Override
    public String getValueDisplayString(@NotNull DBSTypedObject column, Object value,
                                        @NotNull DBDDisplayFormat format) {
        if (value instanceof DBGeometry && format == DBDDisplayFormat.NATIVE) {
            int valueSRID = ((DBGeometry) value).getSRID();
            String strValue = value.toString();
            if (valueSRID != 0 && !strValue.startsWith("SRID=")) {
                strValue = "SRID=" + valueSRID + ";" + strValue;
            }
            return strValue;
        }

        if (format == DBDDisplayFormat.UI && value instanceof DamengDBGeometry) {
            DamengDBGeometry geometry = (DamengDBGeometry) value;
            if (geometry.isTopology()) {
                return geometry.getString();
            }
        }

        return super.getValueDisplayString(column, value, format);
    }

    protected DBGeometry makeGeometryFromWKB(DBCSession session, String wkbString, String type) throws DBCException {
        int srid = 0;
        DBGeometry finGeometry = null;

        try {
            DBCExecutionContext executionContext = session.getExecutionContext();
            Connection conn = ((DamengExecutionContext) executionContext).getConnection(null);
            String sql = "select dmgeo2.st_geomfromWKB(?) from dual";
            if (type.startsWith("SYSGEO.")) {
                sql = "select dmgeo.st_geomfromWKB(?) from dual";
            }
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, wkbString);
            ResultSet resultSet = ps.executeQuery();
            while (resultSet.next()) {
                Struct geoStruct = (Struct) resultSet.getObject(1);
                dmStruct = geoStruct;

                if (type.startsWith("SYSGEO.")) {
                    finGeometry = (DBGeometry) dbGeometryFromGeo1Struct(geoStruct);
                } else {
                    finGeometry = (DBGeometry) dbGeometryFromGeo2Struct(geoStruct);
                }
                if (srid != 0) {
                    finGeometry.setSRID(srid);
                }

                resultSet.close();
                ps.close();

                return finGeometry;
            }
        } catch (Exception e) {
            throw new DBCException("Error parsing WKB value", e);
        }

        return finGeometry;
    }

    protected DBGeometry makeGeometryFromWKT(DBCSession session, String wkt, String type) throws DBCException {
        if (CommonUtils.isEmpty(wkt)) {
            dmStruct = null;
            return new DBGeometry();
        }
        try {
            return parseWKT(session, wkt, type);
        } catch (Throwable e) {
            try {
                // May happen when geometry value was stored inside composite
                return makeGeometryFromWKB(session, wkt, type);
            } catch (Throwable ignored) {
            }
            if (e instanceof RuntimeException || e instanceof DBCException) {
                throw e;
            } else {
                throw new DBCException(e.getMessage(), e);
            }
        }
    }
    
}
