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
package org.jkiss.dbeaver.ext.damengdb.model;

import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBIcon;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.DBSObjectType;

import java.util.HashMap;
import java.util.Map;

/**
 * Object type
 */
public enum DamengObjectType implements DBSObjectType {

    CLUSTER("CLUSTER", null, DBSObject.class, null),
    CONSTRAINT("CONSTRAINT", DBIcon.TREE_CONSTRAINT, DamengTableConstraint.class, null), // fake object
    CONSUMER_GROUP("CONSUMER GROUP", null, DBSObject.class, null), CONTEXT("CONTEXT", null, DBSObject.class, null),
    DIRECTORY("DIRECTORY", null, DBSObject.class, null),
    EVALUATION_CONTEXT("EVALUATION CONTEXT", null, DBSObject.class, null),
    FOREIGN_KEY("FOREIGN KEY", DBIcon.TREE_FOREIGN_KEY, DamengTableForeignKey.class, null), // fake
    // object
    FUNCTION("FUNCTION", DBIcon.TREE_PROCEDURE, DamengProcedureStandalone.class, new ObjectFinder() {
        @Override
        public DamengProcedureStandalone findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.proceduresCache.getObject(monitor, schema, objectName);
        }
    }), DBLINK("DBLINK", DBIcon.TREE_LINK, DamengDBLink.class, new ObjectFinder() // DBLink
    {
        @Override
        public DamengDBLink findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.dbLinkCache.getObject(monitor, schema, objectName);
        }
    }), DOMAIN("DOMAIN", DamengConstants.ICON_DOMAIN, DamengDomain.class, new ObjectFinder() // Domain
    {
        @Override
        public DamengDomain findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.domainsCache.getObject(monitor, schema, objectName);
        }
    }), UD_OPERATOR("UD_OPERATOR", DamengConstants.ICON_UD_OPERATOR, DamengDomain.class, new ObjectFinder() // Domain
    {
        @Override
        public DamengDomain findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.domainsCache.getObject(monitor, schema, objectName);
        }
    }), INDEX("INDEX", DBIcon.TREE_INDEX, DamengTableIndex.class, new ObjectFinder() {
        @Override
        public DamengTableIndex findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.indexCache.getObject(monitor, schema, objectName);
        }
    }), INDEX_PARTITION("INDEX PARTITION", null, DBSObject.class, null),
    INDEXTYPE("INDEXTYPE", null, DBSObject.class, null), JAVA_DATA("JAVA DATA", null, DBSObject.class, null),
    JAVA_RESOURCE("JAVA RESOURCE", null, DBSObject.class, null), JOB("JOB", null, DBSObject.class, null),
    JOB_CLASS("JOB CLASS", null, DBSObject.class, null), LIBRARY("LIBRARY", null, DBSObject.class, null),
    LOB("CONTENT", null, DBSObject.class, null), OPERATOR("OPERATOR", null, DBSObject.class, null),
    PACKAGE("PACKAGE", DBIcon.TREE_PACKAGE, DamengPackage.class, new ObjectFinder() {
        @Override
        public DamengPackage findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.packageCache.getObject(monitor, schema, objectName);
        }
    }), PACKAGE_BODY("PACKAGE BODY", DBIcon.TREE_PACKAGE, DamengPackage.class, new ObjectFinder() {
        @Override
        public DamengPackage findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.packageCache.getObject(monitor, schema, objectName);
        }
    }), PROCEDURE("PROCEDURE", DBIcon.TREE_PROCEDURE, DamengProcedureStandalone.class, new ObjectFinder() {
        @Override
        public DamengProcedureStandalone findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.proceduresCache.getObject(monitor, schema, objectName);
        }
    }), PROGRAM("PROGRAM", null, DBSObject.class, null), RULE("RULE", null, DBSObject.class, null),
    RULE_SET("RULE SET", null, DBSObject.class, null), SCHEDULE("SCHEDULE", null, DBSObject.class, null),
    SEQUENCE("SEQUENCE", DBIcon.TREE_SEQUENCE, DamengSequence.class, new ObjectFinder() {
        @Override
        public DamengSequence findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.sequenceCache.getObject(monitor, schema, objectName);
        }
    }), SYNONYM("SYNONYM", DBIcon.TREE_SYNONYM, DamengSynonym.class, new ObjectFinder() {
        @Override
        public DamengSynonym findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.synonymCache.getObject(monitor, schema, objectName);
        }
    }), TABLE("TABLE", DBIcon.TREE_TABLE, DamengTable.class, new ObjectFinder() {
        @Override
        public DamengTableBase findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.tableCache.getObject(monitor, schema, objectName);
        }
    }), TABLE_PARTITION("TABLE PARTITION", null, DBSObject.class, null),
    TRIGGER("TRIGGER", DBIcon.TREE_TRIGGER, DamengTrigger.class, new ObjectFinder() {
        @Override
        public DamengTrigger<?> findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            DamengTableTrigger trigger = schema.tableTriggerCache.getObject(monitor, schema, objectName);
            if (trigger != null) {
                return trigger;
            }

            DamengDataBaseTrigger databaseTrigger = schema.databaseTriggerCache.getObject(monitor, schema, objectName);

            if (databaseTrigger != null) {
                return databaseTrigger;
            }

            DamengViewTrigger dmViewTrigger = schema.viewTriggerCache.getObject(monitor, schema, objectName);

            if (dmViewTrigger != null) {
                return dmViewTrigger;
            }
            return schema.triggerCache.getObject(monitor, schema, objectName);
        }
    }), CLASS("CLASS", DBIcon.TREE_DATA_TYPE, DamengDataType.class, new ObjectFinder() {
        @Override
        public DamengDataType findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.dataTypeCache.getObject(monitor, schema, objectName);
        }
    }), TYPE("TYPE", DBIcon.TREE_DATA_TYPE, DamengDataType.class, new ObjectFinder() {
        @Override
        public DamengDataType findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.allDataTypeCache.getObject(monitor, schema, objectName);
        }
    }), CLASS_BODY("CLASS_BODY", DBIcon.TREE_DATA_TYPE, DamengDataType.class, new ObjectFinder() {
        @Override
        public DamengDataType findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.dataTypeCache.getObject(monitor, schema, objectName);
        }
    }), VIEW("VIEW", DBIcon.TREE_VIEW, DamengView.class, new ObjectFinder() {
        @Override
        public DamengView findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.tableCache.getObject(monitor, schema, objectName, DamengView.class);
        }
    }), MATERIALIZED_VIEW("MATERIALIZED VIEW", DBIcon.TREE_VIEW, DamengMaterializedView.class, new ObjectFinder() {
        @Override
        public DamengMaterializedView findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName)
            throws DBException {
            return schema.tableCache.getObject(monitor, schema, objectName, DamengMaterializedView.class);
        }
    }), WINDOW("WINDOW", null, DBSObject.class, null), WINDOW_GROUP("WINDOW GROUP", null, DBSObject.class, null),
    XML_SCHEMA("XML SCHEMA", null, DBSObject.class, null);

    private static final Log log = Log.getLog(DamengObjectType.class);

    private static Map<String, DamengObjectType> typeMap = new HashMap<>();

    static {
        for (DamengObjectType type : values()) {
            typeMap.put(type.getTypeName(), type);
        }
    }

    private final String objectType;
    private final DBPImage image;
    private final Class<? extends DBSObject> typeClass;
    private final ObjectFinder finder;

    <OBJECT_TYPE extends DBSObject> DamengObjectType(String objectType, DBPImage image, Class<OBJECT_TYPE> typeClass,
                                                     ObjectFinder finder) {
        this.objectType = objectType;
        this.image = image;
        this.typeClass = typeClass;
        this.finder = finder;
    }

    public static DamengObjectType getByType(String typeName) {
        return typeMap.get(typeName);
    }

    public static Object resolveObject(DBRProgressMonitor monitor, DamengDataSource dataSource, String dbLink,
                                       String objectTypeName, String objectOwner, String objectName) throws DBException {
        if (dbLink != null) {
            return objectName;
        }
        DamengObjectType objectType = DamengObjectType.getByType(objectTypeName);
        if (objectType == null) {
            log.debug("Unrecognized Dm object type: " + objectTypeName);
            return objectName;
        }
        if (!objectType.isBrowsable()) {
            log.debug("Unsupported Dm object type: " + objectTypeName);
            return objectName;
        }
        final DamengSchema schema = dataSource.getSchema(monitor, objectOwner);
        if (schema == null) {
            log.debug("Schema '" + objectOwner + "' not found");
            return objectName;
        }
        final DBSObject object = objectType.findObject(monitor, schema, objectName);
        if (object == null) {
            log.debug(objectTypeName + " '" + objectName + "' not found in '" + schema.getName() + "'");
            return objectName;
        }
        return object;
    }

    public boolean isBrowsable() {
        return finder != null;
    }

    @Override
    public String getTypeName() {
        return objectType;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public DBPImage getImage() {
        return image;
    }

    @Override
    public Class<? extends DBSObject> getTypeClass() {
        return typeClass;
    }

    public DBSObject findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName) throws DBException {
        if (finder != null) {
            return finder.findObject(monitor, schema, objectName);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return objectType;
    }

    private static interface ObjectFinder {
        DBSObject findObject(DBRProgressMonitor monitor, DamengSchema schema, String objectName) throws DBException;
    }

}
