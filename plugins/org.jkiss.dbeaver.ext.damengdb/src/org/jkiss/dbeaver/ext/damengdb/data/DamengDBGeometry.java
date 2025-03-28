/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Struct;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.damengdb.model.DamengExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCException;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBCSession;
import org.jkiss.dbeaver.model.gis.DBGeometry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

public class DamengDBGeometry extends DBGeometry {
	
    private static final DBGeometry NULL_DBGEOMETRY = new DBGeometry();

    private static final DBGeometry EMPTY_DBGEOMETRY;

    static {
        Geometry geometry = null;
        WKTReader wktReader = new WKTReader();
        try {
            geometry = wktReader.read("Point empty");
        } catch (ParseException e) {
            e.printStackTrace();
        }

        EMPTY_DBGEOMETRY = new DBGeometry(geometry);
    }

    private DBGeometry dbGeometry;
    private String text;
    private int gserType;
    private int[] topology;
    private DBCSession session;

    public DamengDBGeometry(DBGeometry dbGeometry, String text, int gserType) {
        super();
        this.dbGeometry = dbGeometry;
        this.text = text;
        this.gserType = gserType;
    }

    public DamengDBGeometry(String text, DBCSession session, int[] topology) {
        super();
        this.dbGeometry = EMPTY_DBGEOMETRY;
        this.text = text;
        this.session = session;
        this.topology = topology;
    }

    @Nullable
    @Override
    public Geometry getGeometry() {
        return dbGeometry.getGeometry();
    }

    public DBGeometry getDBGeometry() {
        return dbGeometry;
    }

    public boolean isTopology() {
        return topology != null;
    }

    @Nullable
    @Override
    public String getString() {
        if (text != null) {
            return text;
        } else {
            return dbGeometry.getString();
        }
    }

    @Override
    public Object getRawValue() {
        Object rawValue = dbGeometry.getRawValue();
        if (rawValue == null) {
            return null;
        }

        String valueText = getString();
        if (valueText.equalsIgnoreCase("POINT EMPTY") || valueText.equalsIgnoreCase("POINT (NaN NaN)")) {
            return null;
        }

        return rawValue;
    }

    @Override
    public boolean isModified() {
        return dbGeometry.isModified();
    }

    @Override
    public void release() {
        dbGeometry.release();
    }

    @Override
    public String toString() {
        if (this.topology != null) {
            if (dbGeometry == EMPTY_DBGEOMETRY) {
                try {
                    force2D();
                } catch (DBException e) {
                    e.printStackTrace();
                    return "";
                }
            }

            return dbGeometry.getString();
        }

        String string = null;
        if (text != null) {
            string = text;
        } else {
            string = dbGeometry.getString();
        }
        if (string == null) {
            return super.toString();
        }

        switch (gserType) {
            case 13:
                string = string.replaceFirst("MULTIPOLYGON", "POLYHEDRALSURFACE");
                break;
            case 14:
                string = string.replaceFirst("POLYGON", "TRIANGLE");
                break;
            case 15:
                string = string.replaceFirst("MULTIPOLYGON", "TIN");
                break;
        }
        return string;
    }

    @Override
    public int getSRID() {
        return dbGeometry.getSRID();
    }

    @Override
    public void setSRID(int srid) {
        dbGeometry.setSRID(srid);
    }

    @Override
    public DBGeometry flipCoordinates() throws DBException {
        return dbGeometry.flipCoordinates();
    }

    @NotNull
    @Override
    public DBGeometry force2D() throws DBException {
        if (this.topology != null) {
            if (dbGeometry == EMPTY_DBGEOMETRY) {
                DamengDBGeometry geometry = null;
                try {
                    DBCExecutionContext executionContext = session.getExecutionContext();
                    Connection conn = ((DamengExecutionContext) executionContext).getConnection(null);
                    String sql = "select systopology.dmtopology.geometry(SYSTOPOLOGY.TOPOGEOMETRY(?,?,?,?));";
                    PreparedStatement ps = conn.prepareStatement(sql);
                    ps.setInt(1, topology[0]);
                    ps.setInt(2, topology[1]);
                    ps.setInt(3, topology[2]);
                    ps.setInt(4, topology[3]);
                    ResultSet resultSet = ps.executeQuery();
                    while (resultSet.next()) {
                        Struct geo2Struct = (Struct) resultSet.getObject(1);
                        geometry = (DamengDBGeometry) DamengGeometryValueHandler.dbGeometryFromGeo2Struct(geo2Struct);
                    }
                    resultSet.close();
                    ps.close();

                    dbGeometry = geometry.getDBGeometry();
                    return dbGeometry.force2D();
                } catch (DBCException e) {
                    e.printStackTrace();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        if (getRawValue() == null) {
            return NULL_DBGEOMETRY;
        }

        try {
            return dbGeometry.force2D();
        } catch (DBException e) {
            if (DamengWKGUtils.dbeaverIs232()) {
                throw e;
            }

            Object targetValue = this.getRawValue();
            if (DamengWKGUtils.isCurve(targetValue)) {
                targetValue = DamengWKGUtils.linearize((org.cugos.wkg.Geometry) targetValue);
            }

            Geometry geometry = DamengWKGUtils.getJtsGeometry(targetValue);
            if (geometry != null) {
                return new DBGeometry(geometry, dbGeometry.getSRID(), dbGeometry.getProperties());
            } else {
                return new DBGeometry(targetValue, dbGeometry.getSRID(), dbGeometry.getProperties());
            }
        }
    }

    @Override
    public Map<String, Object> getProperties() {
        return dbGeometry.getProperties();
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        dbGeometry.setProperties(properties);
    }

    @Override
    public void putProperties(@NotNull Map<String, Object> properties) {
        dbGeometry.putProperties(properties);
    }

    @Override
    public DBGeometry copy() {
        return dbGeometry.copy();
    }

    /**
     * @return true if all geometry points set to zero
     */
    @Override
    public boolean isEmpty() {
        return dbGeometry.isEmpty();
    }

    @Override
    public boolean isNull() {
        return dbGeometry.isNull();
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setGserType(int gserType) {
        this.gserType = gserType;
    }
    
}
