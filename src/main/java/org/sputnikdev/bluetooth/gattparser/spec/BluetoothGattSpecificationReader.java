package org.sputnikdev.bluetooth.gattparser.spec;

/*-
 * #%L
 * org.sputnikdev:bluetooth-gatt-parser
 * %%
 * Copyright (C) 2017 Sputnik Dev
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bluetooth GATT specification reader. Capable of reading Bluetooth SIG GATT specifications for
 * <a href="https://www.bluetooth.com/specifications/gatt">services and characteristics</a>.
 * Stateful but threadsafe.
 *
 * @author Vlad Kolotov
 */
public class BluetoothGattSpecificationReader {

    public static final String MANDATORY_FLAG = "Mandatory";
    public static final String OPTIONAL_FLAG = "Optional";
    public static final String SPEC_LIST_FILE_NAME = "gatt_spec_files.txt";
    public static final String SPEC_ROOT_FOLDER_NAME = "gatt";
    public static final String SPEC_SERVICES_FOLDER_NAME = "service";
    public static final String SPEC_CHARACTERISTICS_FOLDER_NAME = "characteristic";
    public static final String SPEC_FULL_SERVICES_FOLDER_NAME = SPEC_ROOT_FOLDER_NAME + File.separator +
            SPEC_SERVICES_FOLDER_NAME;
    public static final String SPEC_FULL_CHARACTERISTICS_FOLDER_NAME = SPEC_ROOT_FOLDER_NAME + File.separator +
            SPEC_CHARACTERISTICS_FOLDER_NAME;
    public static final String EXTENSION_ROOT_FOLDER_NAME = "ext";
    public static final String EXTENSION_SPEC_SERVICES_FOLDER_NAME =
            EXTENSION_ROOT_FOLDER_NAME + "/" + SPEC_FULL_SERVICES_FOLDER_NAME;
    public static final String EXTENSION_SPEC_CHARACTERISTICS_FOLDER_NAME =
            EXTENSION_ROOT_FOLDER_NAME + "/" + SPEC_FULL_CHARACTERISTICS_FOLDER_NAME;
    private final Logger logger = LoggerFactory.getLogger(BluetoothGattSpecificationReader.class);

    private static FilenameFilter XML_FILE_FILTER = new FilenameFilter() {
        @Override public boolean accept(File dir, String name) {
            return name.toLowerCase().endsWith(".xml");
        }
    };

    private Map<String, Service> services = new HashMap<>();
    private Map<String, Characteristic> characteristicsByUUID = new HashMap<>();
    private Map<String, Characteristic> characteristicsByType = new HashMap<>();

    /**
     * Creates an instance of GATT specification reader and loads GATT specification files:
     * <ul>
     *     <li>Standard specifications from java classpath by the following paths: gatt/characteristic and gatt/service</li>
     *     <li>User-defined specification extensions from java classpath by the following paths: ext/gatt/characteristic and ext/gatt/service</li>
     * </ul>
     *
     */
    public BluetoothGattSpecificationReader() {
        loadFromClassPath();
        loadExtensionsFromClassPath();
    }

    /**
     * Returns GATT service specification by its UUID.
     *
     * @param uuid an UUID of a GATT service
     * @return GATT service specification
     */
    public Service getService(String uuid) {
        return services.get(uuid);
    }

    /**
     * Returns GATT characteristic specification by its UUID.
     *
     * @param uuid an UUID of a GATT characteristic
     * @return GATT characteristic specification
     */
    public Characteristic getCharacteristicByUUID(String uuid) {
        return characteristicsByUUID.get(uuid);
    }

    /**
     * Returns GATT characteristic specification by its type.
     *
     * @param type a type of a GATT characteristic
     * @return GATT characteristic specification
     */
    public Characteristic getCharacteristicByType(String type) {
        return characteristicsByType.get(type);
    }

    /**
     * Returns all registered GATT characteristic specifications.
     *
     * @return all registered characteristic specifications
     */
    public Collection<Characteristic> getCharacteristics() {
        return new ArrayList<>(characteristicsByUUID.values());
    }

    /**
     * Returns all registered GATT service specifications.
     *
     * @return all registered GATT service specifications
     */
    public Collection<Service> getServices() {
        return new ArrayList<>(services.values());
    }

    /**
     * Returns a list of field specifications for a given characteristic.
     * Note that field references are taken into account. Referencing fields are not returned,
     * referenced fields returned instead (see {@link Field#getReference()}).
     *
     * @param characteristic a GATT characteristic specification object
     * @return a list of field specifications for a given characteristic
     */
    public List<Field> getFields(Characteristic characteristic) {
        List<Field> fields = new ArrayList<>();
        if (characteristic.getValue() == null) {
            return Collections.emptyList();
        }
        for (Field field: characteristic.getValue().getFields()) {
            if (field.getReference() == null) {
                fields.add(field);
            } else {
                fields.addAll(getFields(getCharacteristicByType(field.getReference().trim())));
            }
        }
        return Collections.unmodifiableList(fields);
    }

    /**
     * This method is used to load/register custom services and characteristics
     * (defined in GATT XML specification files,
     * see an example <a href="https://www.bluetooth.com/api/gatt/XmlFile?xmlFileName=org.bluetooth.characteristic.battery_level.xml">here</a>)
     * from a folder. The folder must contain two sub-folders for services and characteristics respectively:
     * "path"/service and "path"/characteristic. It is also possible to override existing services and characteristics
     * by matching UUIDs of services and characteristics in the loaded files.
     * @param path a root path to a folder containing definitions for custom services and characteristics
     */
    public void loadExtensionsFromFolder(String path) {
        logger.info("Reading services and characteristics from folder: " + path);
        String servicesFolderName = path + File.separator + SPEC_SERVICES_FOLDER_NAME;
        String characteristicsFolderName = path + File.separator + SPEC_CHARACTERISTICS_FOLDER_NAME;
        logger.info("Reading services from folder: " + servicesFolderName);
        readServices(getFilesFromFolder(servicesFolderName));
        logger.info("Reading characteristics from folder: " + characteristicsFolderName);
        readCharacteristics(getFilesFromFolder(characteristicsFolderName));
    }

    Set<String> getAllReadFlags(Characteristic characteristic) {
        if (characteristic.getValue() == null || characteristic.getValue().getFlags() == null) {
            return Collections.EMPTY_SET;
        }
        return FlagUtils.getAllReadFlags(characteristic.getValue().getFlags());
    }

    Set<String> getAllWriteFlags(Characteristic characteristic) {
        if (characteristic.getValue() == null || characteristic.getValue().getFlags() == null) {
            return Collections.EMPTY_SET;
        }
        return FlagUtils.getAllWriteFlags(characteristic.getValue().getFlags());
    }


    Set<String> getRequirements(Characteristic characteristic) {
        Set<String> result = new HashSet<>();
        if (characteristic.getValue() == null || characteristic.getValue().getFields() == null) {
            logger.warn("Characteristic \"{}\" does not have either Value or Fields tags, "
                    + "therefore reading the such characteristic will not be possible.", characteristic.getName());
            return result;
        }
        for (Iterator<Field> iterator = characteristic.getValue().getFields().iterator(); iterator.hasNext();) {
            Field field = iterator.next();
            if (field.getBitField() != null) {
                continue;
            }
            List<String> requirements = field.getRequirements();
            if (requirements == null || requirements.isEmpty()) {
                continue;
            }
            if (requirements.contains(MANDATORY_FLAG)) {
                continue;
            }
            if (requirements.size() == 1 && requirements.contains(OPTIONAL_FLAG) && !iterator.hasNext()) {
                continue;
            }
            result.addAll(requirements);
        }
        return result;
    }

    private void loadFromClassPath() {
        logger.info("Reading services from folder: " + SPEC_FULL_SERVICES_FOLDER_NAME);
        readServices(getFilesFromClassPath(SPEC_FULL_SERVICES_FOLDER_NAME));
        logger.info("Reading characteristics from folder: " + SPEC_FULL_CHARACTERISTICS_FOLDER_NAME);
        readCharacteristics(getFilesFromClassPath(SPEC_FULL_CHARACTERISTICS_FOLDER_NAME));
    }

    private void loadExtensionsFromClassPath() {
        logger.info("Reading services extensions from folder: " + EXTENSION_SPEC_SERVICES_FOLDER_NAME);
        readServices(getFilesFromClassPath(EXTENSION_SPEC_SERVICES_FOLDER_NAME));
        logger.info("Reading characteristics extensions from folder: " + EXTENSION_SPEC_CHARACTERISTICS_FOLDER_NAME);
        readCharacteristics(getFilesFromClassPath(EXTENSION_SPEC_CHARACTERISTICS_FOLDER_NAME));
    }

    private void addCharacteristic(Characteristic characteristic) {
        validate(characteristic);
        characteristicsByUUID.put(characteristic.getUuid(), characteristic);
        characteristicsByType.put(characteristic.getType().trim(), characteristic);
    }

    private void addService(Service service) {
        services.put(service.getUuid(), service);
    }

    private void validate(Characteristic characteristic) {
        Set<String> readFlags = getAllReadFlags(characteristic);
        Set<String> writeFlags = getAllWriteFlags(characteristic);
        Set<String> requirements = getRequirements(characteristic);

        Set<String> unfulfilledReadRequirements = new HashSet<>(requirements);
        unfulfilledReadRequirements.removeAll(readFlags);
        Set<String> unfulfilledWriteRequirements = new HashSet<>(requirements);
        unfulfilledWriteRequirements.removeAll(writeFlags);

        if (unfulfilledReadRequirements.isEmpty()) {
            characteristic.setValidForRead(true);
        }

        if (unfulfilledWriteRequirements.isEmpty()) {
            characteristic.setValidForWrite(true);
        }

        if (!unfulfilledReadRequirements.isEmpty() && !unfulfilledWriteRequirements.isEmpty()) {
            logger.warn("Characteristic \"{}\" is not valid neither for read nor for write operation "
                    + "due to unfulfilled requirements: read ({}) write ({}).",
                    characteristic.getName(), unfulfilledReadRequirements, unfulfilledWriteRequirements);
        }
    }

    private List<URL> getFilesFromClassPath(String folder) {
        ClassLoader classLoader = getClass().getClassLoader();
        if (classLoader.getResource(folder) == null) {
            return Collections.emptyList();
        }
        String path = folder;
        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }
        URL serviceRegistry = getClass().getClassLoader().getResource(path + SPEC_LIST_FILE_NAME);
        if (serviceRegistry != null) {
            logger.debug("Spec list file found in folder: " + folder);
            return getFilesFromClassPath(path, serviceRegistry);
        } else {
            logger.debug("Could not find spec list file in folder: " + folder);
            return getAllFilesFromClassPath(folder);
        }
    }

    private List<URL> getFilesFromFolder(String folder) {
        File folderFile = new File(folder);
        File[] files = folderFile.listFiles();
        if (!folderFile.exists() || !folderFile.isDirectory() || files == null || files.length == 0) {
            return Collections.emptyList();
        }
        List<URL> urls = new ArrayList<>();
        try {
            for (File file : files) {
                urls.add(file.toURI().toURL());
            }
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        return urls;
    }

    private List<URL> getFilesFromClassPath(String rootFolder, URL fileList) {
        logger.debug("Getting spec list from file: " + fileList.getPath());
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            List<URL> files = new ArrayList<>();
            String content = new Scanner(fileList.openStream(), "UTF-8").useDelimiter("\\A").next();
            for (String fileName : content.split("\\r?\\n")) {
                URL file = classLoader.getResource(rootFolder + fileName.trim());
                if (file != null) {
                    files.add(file);
                }
            }
            logger.debug("Found specs: " + files.size());
            return files;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<URL> getAllFilesFromClassPath(String rootFolder) {
        logger.debug("Getting all specs from folder: " + rootFolder);
        try {
            ClassLoader classLoader = getClass().getClassLoader();
            List<URL> files = new ArrayList<>();
            for (File file : new File(classLoader.getResource(rootFolder).toURI()).listFiles(XML_FILE_FILTER)) {
                files.add(file.toURI().toURL());
            }
            logger.debug("Found specs: " + files.size());
            return files;
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void readServices(List<URL> files) {
        for (URL file : files) {
            Service service = getService(file);
            if (service != null) {
                addService(service);
            }
        }
    }

    private void readCharacteristics(List<URL> files) {
        for (URL file : files) {
            Characteristic characteristic = getCharacteristic(file);
            if (characteristic != null) {
                addCharacteristic(characteristic);
            }
        }
    }

    private Service getService(URL file) {
        return getSpec(file);
    }

    private Characteristic getCharacteristic(URL file) {
        return getSpec(file);
    }

    private <T> T getSpec(URL file) {
        try {
            XStream xstream = new XStream(new DomDriver());
            xstream.autodetectAnnotations(true);
            xstream.processAnnotations(Bit.class);
            xstream.processAnnotations(BitField.class);
            xstream.processAnnotations(Characteristic.class);
            xstream.processAnnotations(Enumeration.class);
            xstream.processAnnotations(Enumerations.class);
            xstream.processAnnotations(Field.class);
            xstream.processAnnotations(InformativeText.class);
            xstream.processAnnotations(Service.class);
            xstream.processAnnotations(Value.class);
            xstream.processAnnotations(Reserved.class);
            xstream.processAnnotations(Examples.class);
            xstream.processAnnotations(CharacteristicAccess.class);
            xstream.processAnnotations(Characteristics.class);
            xstream.processAnnotations(Properties.class);
            xstream.ignoreUnknownElements();
            xstream.setClassLoader(Characteristic.class.getClassLoader());
            return (T) xstream.fromXML(file);
        } catch (Exception e) {
            logger.error("Could not read file: " + file, e);
        }
        return null;
    }

}
