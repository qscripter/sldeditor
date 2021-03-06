/*
 * SLD Editor - The Open Source Java SLD Editor
 *
 * Copyright (C) 2016, SCISYS UK Limited
 *
 * This program is free software: you can redistribute it and/or modify
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
package com.sldeditor.datasource.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.geotools.coverage.grid.io.AbstractGridCoverage2DReader;
import org.geotools.data.DataAccessFactory;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFactorySpi;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureSource;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.styling.UserLayer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.feature.type.PropertyDescriptor;

import com.sldeditor.common.DataSourcePropertiesInterface;
import com.sldeditor.common.SLDDataInterface;
import com.sldeditor.common.console.ConsoleManager;
import com.sldeditor.datasource.DataSourceInterface;
import com.sldeditor.datasource.DataSourceUpdatedInterface;
import com.sldeditor.datasource.SLDEditorFileInterface;
import com.sldeditor.datasource.attribute.AllowedAttributeTypes;
import com.sldeditor.datasource.attribute.DataSourceAttributeData;
import com.sldeditor.datasource.attribute.DataSourceAttributeList;
import com.sldeditor.datasource.attribute.DataSourceAttributeListInterface;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Class that represents data sources for an SLD symbol. Provides functionality to read and update its schema. Handles the following:
 * <p>
 * - example data
 * <p>
 * - external data (e.g shape file, tiff)
 * <p>
 * - user layer inline data
 * <p>
 * 
 * @author Robert Ward (SCISYS)
 */
public class DataSourceImpl implements DataSourceInterface {

    /** The Constant MAX_RETRIES. */
    private static final int MAX_RETRIES = 3;

    /** The logger. */
    private static Logger logger = Logger.getLogger(DataSourceImpl.class);

    /** The listener list. */
    private List<DataSourceUpdatedInterface> listenerList = new ArrayList<DataSourceUpdatedInterface>();

    /** The data source info. */
    private DataSourceInfo dataSourceInfo = new DataSourceInfo();

    /** The example data source info, used to draw the single rendered symbol. */
    private DataSourceInfo exampleDataSourceInfo = new DataSourceInfo();

    /** The user layer data source info. */
    private List<DataSourceInfo> userLayerDataSourceInfo = new ArrayList<DataSourceInfo>();

    /** The data source properties. */
    private DataSourcePropertiesInterface dataSourceProperties = null;

    /** The connected to data source flag. */
    private boolean connectedToDataSourceFlag = false;

    /** The available data stores. */
    private List<String> availableDataStoreList = new ArrayList<String>();

    /** The editor file interface. */
    private SLDEditorFileInterface editorFileInterface = null;

    /** The internal data source. */
    private CreateDataSourceInterface internalDataSource = null;

    /** The external data source. */
    private CreateDataSourceInterface externalDataSource = null;

    /** The inline data source. */
    private CreateDataSourceInterface inlineDataSource = null;

    /**
     * Default constructor.
     */
    public DataSourceImpl() {
        populateAvailableDataStores();
    }

    /**
     * Sets the data source creation classes.
     *
     * @param internalDataSource the internal data source
     * @param externalDataSource the external data source
     * @param inlineDataSource the inline data source
     */
    @Override
    public void setDataSourceCreation(CreateDataSourceInterface internalDataSource,
            CreateDataSourceInterface externalDataSource,
            CreateDataSourceInterface inlineDataSource) {
        this.internalDataSource = internalDataSource;
        this.externalDataSource = externalDataSource;
        this.inlineDataSource = inlineDataSource;
    }

    /**
     * Adds the data source updated listener.
     *
     * @param listener the listener
     */
    @Override
    public void addListener(DataSourceUpdatedInterface listener) {
        if (!listenerList.contains(listener)) {
            listenerList.add(listener);

            if (getGeometryType() != GeometryTypeEnum.UNKNOWN) {
                notifyDataSourceLoaded();
            }
        }
    }

    /**
     * Removes the data source updated listener.
     *
     * @param listener the listener
     */
    @Override
    public void removeListener(DataSourceUpdatedInterface listener) {
        listenerList.remove(listener);
    }

    /**
     * Connect to data source.
     *
     * @param editorFile the editor file
     */
    @Override
    public void connect(SLDEditorFileInterface editorFile) {
        reset();

        this.editorFileInterface = editorFile;

        if (editorFileInterface != null) {
            this.dataSourceProperties = editorFile.getDataSource();

            if (this.dataSourceProperties != null) {
                if (this.dataSourceProperties.isEmpty()) {
                    openWithoutDataSource();
                } else {
                    openExternalDataSource();
                }

                // Create the example data to show in the render panel
                createExampleDataSource();

                createUserLayerDataSources();

                notifyDataSourceLoaded();
            }
        }
    }

    /**
     * Create inline data sources
     */
    private void createUserLayerDataSources() {
        if (inlineDataSource == null) {
            ConsoleManager.getInstance().error(this, "No inline data source creation object set");
        } else {
            userLayerDataSourceInfo = inlineDataSource.connect(null, this.editorFileInterface);

            if (userLayerDataSourceInfo != null) {
                for (DataSourceInfo dsInfo : userLayerDataSourceInfo) {
                    if (dsInfo.hasData()) {
                        dsInfo.populateFieldMap();
                    }
                }
            }
        }
    }

    /**
     * Open external data source.
     */
    private void openExternalDataSource() {
        if (externalDataSource == null) {
            ConsoleManager.getInstance().error(this, "No external data source creation object set");
        } else {
            List<DataSourceInfo> dataSourceInfoList = externalDataSource.connect(null,
                    this.editorFileInterface);
            if ((dataSourceInfoList != null) && (dataSourceInfoList.size() == 1)) {
                dataSourceInfo = dataSourceInfoList.get(0);

                if (dataSourceInfo.hasData()) {
                    dataSourceInfo.populateFieldMap();

                    connectedToDataSourceFlag = true;
                } else {
                    openWithoutDataSource();
                }
            }

            // Populate external fields
            DataSourceAttributeList attributeData = new DataSourceAttributeList();
            readAttributes(attributeData);
            SLDDataInterface sldData = this.editorFileInterface.getSLDData();

            sldData.setFieldList(attributeData.getData());
        }
    }

    /**
     * Populates the list of available data stores that can be connected to.
     */
    private void populateAvailableDataStores() {
        DataAccessFactory fac;

        logger.debug("Available data store factories:");

        Iterator<DataStoreFactorySpi> iterator = DataStoreFinder.getAvailableDataStores();
        while (iterator.hasNext()) {
            fac = (DataAccessFactory) iterator.next();

            logger.debug("\t" + fac.getDisplayName());

            availableDataStoreList.add(fac.getDisplayName());
        }
    }

    /**
     * Unload data store.
     */
    private void unloadDataStore() {
        if (dataSourceInfo != null) {
            // Tell any listeners that the data store is about to be disposed of
            notifyDataSourceAboutToUnloaded(dataSourceInfo.getDataStore());
            dataSourceInfo.unloadDataStore();
        }

        if (exampleDataSourceInfo != null) {
            notifyDataSourceAboutToUnloaded(exampleDataSourceInfo.getDataStore());
            exampleDataSourceInfo.unloadDataStore();
        }
    }

    /**
     * Gets the feature source.
     *
     * @return the feature source
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.impl.DataSourceInterface#getFeatureSource()
     */
    @Override
    public FeatureSource<SimpleFeatureType, SimpleFeature> getFeatureSource() {
        FeatureSource<SimpleFeatureType, SimpleFeature> features = dataSourceInfo.getFeatures();

        return features;
    }

    /**
     * Gets the example feature source.
     *
     * @return the example feature source
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.impl.DataSourceInterface#getExampleFeatureSource()
     */
    @Override
    public FeatureSource<SimpleFeatureType, SimpleFeature> getExampleFeatureSource() {
        if (exampleDataSourceInfo != null) {
            FeatureSource<SimpleFeatureType, SimpleFeature> features = exampleDataSourceInfo
                    .getFeatures();

            return features;
        }
        return null;
    }

    /**
     * Gets the attributes.
     *
     * @param expectedDataType the expected data type
     * @return the attributes
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.impl.DataSourceInterface#getAttributes(java.lang.Class)
     */
    @Override
    public List<String> getAttributes(Class<?> expectedDataType) {
        List<String> attributeNameList = new ArrayList<String>();

        Collection<PropertyDescriptor> descriptorList = getPropertyDescriptorList();

        if (descriptorList != null) {
            for (PropertyDescriptor property : descriptorList) {
                Class<?> bindingType = property.getType().getBinding();
                if (AllowedAttributeTypes.isAllowed(bindingType, expectedDataType)) {
                    attributeNameList.add(property.getName().toString());
                }
            }
        }
        return attributeNameList;
    }

    /**
     * Gets the geometry type.
     *
     * @return the geometry type
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#getGeometryType()
     */
    @Override
    public GeometryTypeEnum getGeometryType() {
        return (dataSourceInfo != null) ? dataSourceInfo.getGeometryType()
                : GeometryTypeEnum.UNKNOWN;
    }

    /**
     * Read attributes.
     *
     * @param attributeData the attribute data
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#updateAttributes(com.sldeditor.render.iface.RenderAttributeDataInterface)
     */
    @Override
    public void readAttributes(DataSourceAttributeListInterface attributeData) {
        if (attributeData == null) {
            return;
        }

        List<DataSourceAttributeData> valueMap = new ArrayList<DataSourceAttributeData>();

        SimpleFeatureCollection featureCollection = dataSourceInfo.getFeatureCollection();
        if (featureCollection != null) {
            SimpleFeatureIterator iterator = featureCollection.features();

            Map<Integer, Name> fieldNameMap = dataSourceInfo.getFieldNameMap();
            Map<Integer, Class<?>> fieldTypeMap = dataSourceInfo.getFieldTypeMap();

            if (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();

                List<Object> attributes = feature.getAttributes();
                for (int i = 0; i < attributes.size(); i++) {
                    Name name = fieldNameMap.get(i);
                    if (name != null) {
                        String fieldName = name.getLocalPart();

                        Class<?> fieldType = fieldTypeMap.get(i);

                        if (fieldType == Geometry.class) {
                            Object value = feature.getAttribute(fieldName);

                            fieldType = value.getClass();
                        }

                        Object value = CreateSampleData.getFieldTypeValue(i, fieldName, fieldType,
                                attributes.get(i));

                        DataSourceAttributeData data = new DataSourceAttributeData(fieldName,
                                fieldType, value);

                        valueMap.add(data);
                    }
                }
            }

            iterator.close();
        }

        attributeData.setData(valueMap);
    }

    /**
     * Reset.
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#reset()
     */
    @Override
    public void reset() {
        unloadDataStore();

        dataSourceInfo.reset();
        dataSourceProperties = null;

        if (exampleDataSourceInfo != null) {
            exampleDataSourceInfo.reset();
            exampleDataSourceInfo = null;
        }
    }

    /**
     * Open without data source.
     */
    private void openWithoutDataSource() {

        connectedToDataSourceFlag = false;

        if (this.editorFileInterface.getSLD() == null)
            return;

        createInternalDataSource();
    }

    /**
     * Creates the internal data source.
     */
    private void createInternalDataSource() {

        if (internalDataSource == null) {
            ConsoleManager.getInstance().error(this, "No internal data source creation object set");
        } else {
            int attempt = 0;
            boolean retry = true;
            while (retry && (attempt < MAX_RETRIES)) {
                List<DataSourceInfo> dataSourceInfoList = internalDataSource
                        .connect(dataSourceInfo.getGeometryFieldName(), this.editorFileInterface);
                if ((dataSourceInfoList != null) && (dataSourceInfoList.size() == 1)) {
                    dataSourceInfo = dataSourceInfoList.get(0);
                }

                // Check that the field data types that were guessed are correct
                if (ExtractValidFieldTypes.fieldTypesUpdated()) {
                    // Field types were updated so retry
                    attempt++;
                } else {
                    // No changes made to exit
                    retry = false;
                }
            }
        }
    }

    /**
     * Creates the example data source.
     */
    private void createExampleDataSource() {
        if (internalDataSource == null) {
            ConsoleManager.getInstance().error(this, "No internal data source creation object set");
        } else {
            List<DataSourceInfo> dataSourceInfoList = internalDataSource
                    .connect(dataSourceInfo.getGeometryFieldName(), this.editorFileInterface);

            if ((dataSourceInfoList != null) && (dataSourceInfoList.size() == 1)) {
                exampleDataSourceInfo = dataSourceInfoList.get(0);
            }
        }
    }

    /**
     * Notify data source loaded.
     */
    private void notifyDataSourceLoaded() {
        List<DataSourceUpdatedInterface> copyListenerList = new ArrayList<DataSourceUpdatedInterface>(
                listenerList);
        for (DataSourceUpdatedInterface listener : copyListenerList) {
            listener.dataSourceLoaded(getGeometryType(), this.connectedToDataSourceFlag);
        }
    }

    /**
     * Notify data source about to be unloaded.
     *
     * @param dataStore the data store to be unloaded
     */
    private void notifyDataSourceAboutToUnloaded(DataStore dataStore) {
        List<DataSourceUpdatedInterface> copyListenerList = new ArrayList<DataSourceUpdatedInterface>(
                listenerList);
        for (DataSourceUpdatedInterface listener : copyListenerList) {
            listener.dataSourceAboutToUnloaded(dataStore);
        }
    }

    /**
     * Gets the data connector properties.
     *
     * @return the data connector properties
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#getDataConnectorProperties()
     */
    @Override
    public DataSourcePropertiesInterface getDataConnectorProperties() {
        return dataSourceProperties;
    }

    /**
     * Gets the available data store list.
     *
     * @return the available data store list
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#getAvailableDataStoreList()
     */
    @Override
    public List<String> getAvailableDataStoreList() {
        return availableDataStoreList;
    }

    /**
     * Update fields.
     *
     * @param attributeData the attribute data
     */
    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#updateFields(com.sldeditor.render.iface.RenderAttributeDataInterface)
     */
    @Override
    public void updateFields(DataSourceAttributeListInterface attributeData) {
        if (attributeData != null) {
            List<DataSourceAttributeData> attributeDataList = attributeData.getData();

            if (this.editorFileInterface != null) {
                SLDDataInterface sldData = this.editorFileInterface.getSLDData();
                if (sldData != null) {
                    sldData.setFieldList(attributeDataList);
                }
            }
            createInternalDataSource();

            notifyDataSourceLoaded();
        }
    }

    /**
     * Adds the field.
     *
     * @param dataSourceField the data source field
     */
    @Override
    public void addField(DataSourceAttributeData dataSourceField) {
        if (dataSourceField != null) {
            if (connectedToDataSourceFlag == false) {
                SLDDataInterface sldData = this.editorFileInterface.getSLDData();
                List<DataSourceAttributeData> fieldList = sldData.getFieldList();

                if (fieldList == null) {
                    fieldList = new ArrayList<DataSourceAttributeData>();
                    sldData.setFieldList(fieldList);
                }
                fieldList.add(dataSourceField);

                createInternalDataSource();

                notifyDataSourceLoaded();
            }
        }
    }

    /**
     * Gets the property descriptor list.
     *
     * @return the property descriptor list
     */
    @Override
    public Collection<PropertyDescriptor> getPropertyDescriptorList() {
        if (dataSourceInfo != null) {
            return dataSourceInfo.getPropertyDescriptorList();
        }
        return null;
    }

    /**
     * Gets the grid coverage reader.
     *
     * @return the grid coverage reader
     */
    @Override
    public AbstractGridCoverage2DReader getGridCoverageReader() {
        AbstractGridCoverage2DReader gridCoverage = null;

        if (dataSourceInfo != null) {
            gridCoverage = dataSourceInfo.getGridCoverageReader();
        }
        return gridCoverage;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#getUserLayerFeatureSource()
     */
    @Override
    public Map<UserLayer, FeatureSource<SimpleFeatureType, SimpleFeature>> getUserLayerFeatureSource() {
        Map<UserLayer, FeatureSource<SimpleFeatureType, SimpleFeature>> map = new HashMap<UserLayer, FeatureSource<SimpleFeatureType, SimpleFeature>>();

        for (DataSourceInfo dsInfo : userLayerDataSourceInfo) {
            FeatureSource<SimpleFeatureType, SimpleFeature> features = dsInfo.getFeatures();
            UserLayer userLayer = dsInfo.getUserLayer();

            map.put(userLayer, features);
        }
        return map;
    }

    /**
     * Recreate inline data sources for user layers.
     */
    @Override
    public void updateUserLayers() {
        createUserLayerDataSources();

        notifyDataSourceLoaded();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#updateFieldType(java.lang.String, java.lang.Class)
     */
    @Override
    public void updateFieldType(String fieldName, Class<?> dataType) {
        List<DataSourceAttributeData> fieldList = this.editorFileInterface.getSLDData()
                .getFieldList();
        for (DataSourceAttributeData field : fieldList) {
            if (field.getName().compareTo(fieldName) == 0) {
                field.setType(dataType);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sldeditor.datasource.DataSourceInterface#getGeometryFieldName()
     */
    @Override
    public String getGeometryFieldName() {
        return dataSourceInfo.getGeometryFieldName();
    }
}
