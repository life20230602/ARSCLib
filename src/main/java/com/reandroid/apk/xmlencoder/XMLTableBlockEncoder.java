/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.apk.xmlencoder;

import com.reandroid.apk.*;
import com.reandroid.archive.BlockInputSource;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.coder.ReferenceString;
import com.reandroid.arsc.coder.xml.XmlCoder;
import com.reandroid.utils.HexUtil;
import com.reandroid.utils.io.IOUtil;
import com.reandroid.json.JSONObject;
import com.reandroid.xml.XMLDocument;
import com.reandroid.xml.XMLElement;
import com.reandroid.xml.XMLFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;

public class XMLTableBlockEncoder {
    private APKLogger apkLogger;
    private final TableBlock tableBlock;
    private final Set<File> parsedFiles = new HashSet<>();
    private final ApkModule apkModule;
    private Integer mMainPackageId;

    public XMLTableBlockEncoder(ApkModule apkModule, TableBlock tableBlock){
        this.apkModule = apkModule;
        this.tableBlock = tableBlock;
        if(!apkModule.hasTableBlock()){
            BlockInputSource<TableBlock> inputSource =
                    new BlockInputSource<>(TableBlock.FILE_NAME, tableBlock);
            inputSource.setMethod(ZipEntry.STORED);
            this.apkModule.setTableBlock(this.tableBlock);
            apkModule.setLoadDefaultFramework(true);
        }
        apkLogger = apkModule.getApkLogger();
    }
    public XMLTableBlockEncoder(){
        this(new ApkModule("encoded",
                new ZipEntryMap()), new TableBlock());
    }

    public Integer getMainPackageId() {
        return mMainPackageId;
    }

    public TableBlock getTableBlock(){
        return tableBlock;
    }
    public ApkModule getApkModule(){
        return apkModule;
    }
    public void scanMainDirectory(File mainDirectory) throws IOException {
        File resourcesDirectory = new File(mainDirectory, TableBlock.DIRECTORY_NAME);
        scanResourcesDirectory(resourcesDirectory);
    }
    public void scanResourcesDirectory(File resourcesDirectory) throws IOException {
        try {
            scanResourceFiles(resourcesDirectory);
        } catch (XmlPullParserException ex) {
            throw new IOException(ex);
        }
    }
    private void scanResourceFiles(File resourcesDirectory) throws IOException, XmlPullParserException {
        List<File> pubXmlFileList = ApkUtil.listPublicXmlFiles(resourcesDirectory);
        if(pubXmlFileList.size() == 0){
            throw new IOException("No .*/values/"
                    + PackageBlock.PUBLIC_XML
                    + "  file found in '" +resourcesDirectory + "'");
        }
        //preloadStringPool(pubXmlFileList);

        loadPublicXmlFiles(pubXmlFileList);

        excludeIds(pubXmlFileList);
        initializeFrameworkFromManifest(pubXmlFileList);

        encodeAttrs(pubXmlFileList);

        encodeValues(pubXmlFileList);

        tableBlock.refresh();

    }
    private void loadPublicXmlFiles(List<File> pubXmlFileList) throws IOException {
        for(File pubXmlFile:pubXmlFileList){
            loadPublicXmlFile(pubXmlFile);
        }
    }
    private void loadPublicXmlFile(File pubXmlFile) throws IOException {
        try {
            XmlPullParser parser = XMLFactory.newPullParser(pubXmlFile);
            PackageBlock packageBlock = tableBlock.parsePublicXml(parser);
            packageBlock.setTag(pubXmlFile);
            loadPackageJson(packageBlock, pubXmlFile);
            IOUtil.close(parser);
        } catch (XmlPullParserException ex) {
           throw new IOException(ex);
        }
    }
    private void loadPackageJson(PackageBlock packageBlock, File publicXml) throws IOException {
        File json = toPackageJson(publicXml);
        if(json == null){
            return;
        }
        packageBlock.fromJson(new JSONObject(json));
    }
    private File toPackageJson(File publicXml){
        File dir = publicXml.getParentFile();
        //values
        if(dir == null || !"values".equals(dir.getName())){
            return null;
        }
        dir = dir.getParentFile();
        //res
        if(dir == null){
            return null;
        }
        dir = dir.getParentFile();
        if(dir == null){
            return null;
        }
        File json = new File(dir, "package.json");
        if(!json.isFile()){
            return null;
        }
        return json;
    }
    private void initializeFrameworkFromManifest(List<File> pubXmlFileList) throws  IOException {
        for(File pubXmlFile:pubXmlFileList){
            File manifestFile = toAndroidManifest(pubXmlFile);
            if(!manifestFile.isFile()){
                continue;
            }
            initializeFrameworkFromManifest(manifestFile);
            return;
        }
    }
    private void encodeValues(List<File> pubXmlFileList) throws IOException, XmlPullParserException {
        logMessage("Encoding values ...");
        FilePathEncoder filePathEncoder = new FilePathEncoder(getApkModule());
        TableBlock tableBlock = getTableBlock();

        for(File pubXmlFile:pubXmlFileList){
            addParsedFiles(pubXmlFile);
            PackageBlock packageBlock = tableBlock.getPackageBlockByTag(pubXmlFile);
            tableBlock.setCurrentPackage(packageBlock);

            File resDir = toResDirectory(pubXmlFile);
            encodeResDir(resDir);

            filePathEncoder.setApkLogger(getApkLogger());
            filePathEncoder.encodePackageResDir(packageBlock, resDir);

            packageBlock.sortTypes();
            packageBlock.refresh();
        }
    }
    private void encodeAttrs(List<File> pubXmlFileList) throws IOException, XmlPullParserException {
        logMessage("Encoding attrs ...");

        TableBlock tableBlock = getTableBlock();

        for(File pubXmlFile : pubXmlFileList){
            addParsedFiles(pubXmlFile);

            PackageBlock packageBlock = tableBlock.getPackageBlockByTag(pubXmlFile);
            tableBlock.setCurrentPackage(packageBlock);

            List<File> attrFiles = listAttrs(pubXmlFile);
            if(attrFiles.size() == 0){
                continue;
            }
            for(File file : attrFiles){
                logVerbose("Encoding: " + IOUtil.shortPath(file, 4));
                XmlCoder xmlCoder = XmlCoder.getInstance();
                xmlCoder.VALUES_XML.encode(file, packageBlock);
                addParsedFiles(file);
            }
            packageBlock.sortTypes();
        }
    }
    private void excludeIds(List<File> pubXmlFileList){
        for(File pubXmlFile : pubXmlFileList){
            addParsedFiles(pubXmlFile);
            File valuesDir = pubXmlFile.getParentFile();
            File file = new File(valuesDir, "ids.xml");
            if(file.isFile()){
                addParsedFiles(file);
            }
        }
    }
    private void initializeFrameworkFromManifest(File manifestFile) throws IOException {
        if(AndroidManifestBlock.FILE_NAME_BIN.equals(manifestFile.getName())){
            initializeFrameworkFromBinaryManifest();
            return;
        }
        XMLDocument xmlDocument;
        try {
            xmlDocument = XMLDocument.load(manifestFile);
        } catch (XmlPullParserException ex) {
            throw new IOException(ex);
        }
        TableBlock tableBlock = getTableBlock();
        FrameworkApk frameworkApk = getApkModule().initializeAndroidFramework(xmlDocument);
        if(frameworkApk != null){
            tableBlock.addFramework(frameworkApk.getTableBlock());
        }
        initializeMainPackageId(xmlDocument);
    }
    private void initializeFrameworkFromBinaryManifest() throws IOException {
        ApkModule apkModule = getApkModule();
        if(!apkModule.hasTableBlock() || !apkModule.hasAndroidManifestBlock()){
            return;
        }
        logMessage("Initialize framework from binary manifest ...");
        FrameworkApk frameworkApk = apkModule.initializeAndroidFramework(
                apkModule.getAndroidFrameworkVersion());
        getTableBlock().addFramework(frameworkApk.getTableBlock());
    }
    private void initializeMainPackageId(XMLDocument xmlDocument) {
        XMLElement manifestRoot = xmlDocument.getDocumentElement();
        if(manifestRoot == null){
            return;
        }
        XMLElement application = manifestRoot.getElement(AndroidManifestBlock.TAG_application);
        if(application == null){
            return;
        }

        String iconReference = application.getAttributeValue(AndroidManifestBlock.NAME_icon);
        if(iconReference == null){
            return;
        }
        logMessage("Set main package id from manifest: " + iconReference);
        ReferenceString ref = ReferenceString.parseReference(iconReference);
        if(ref == null){
            logMessage("Something wrong on : " + AndroidManifestBlock.NAME_icon);
            return;
        }
        TableBlock tableBlock = getTableBlock();
        int resourceId = tableBlock.resolveResourceId(ref.packageName, ref.type, ref.name);
        if(resourceId == 0){
            logMessage("WARN: failed to resolve: " + ref);
            return;
        }
        int packageId = (resourceId >> 24 ) & 0xff;
        this.mMainPackageId = packageId;
        logMessage("Main package id initialized: id = "
                + HexUtil.toHex2((byte)packageId) + ", from: " + ref );
    }
    private void encodeResDir(File resDir) throws IOException, XmlPullParserException {
        List<File> valuesDirList = ApkUtil.listValuesDirectory(resDir);
        for(File valuesDir : valuesDirList){
            encodeValuesDir(valuesDir);
        }
    }
    private void encodeValuesDir(File valuesDir) throws IOException, XmlPullParserException {
        List<File> xmlFiles = ApkUtil.listFiles(valuesDir, ".xml");
        EncodeUtil.sortValuesXml(xmlFiles);
        for(File file:xmlFiles){
            if(isAlreadyParsed(file)){
                continue;
            }
            addParsedFiles(file);
            logVerbose("Encoding: " + IOUtil.shortPath(file, 4));
            XmlCoder xmlCoder = XmlCoder.getInstance();
            xmlCoder.VALUES_XML.encode(file, getTableBlock().getCurrentPackage());
        }
    }
    private File toAndroidManifest(File pubXmlFile){
        File resDirectory = toResDirectory(pubXmlFile);
        File packageDirectory = resDirectory.getParentFile();
        File resourcesDir = packageDirectory.getParentFile();
        File root = resourcesDir.getParentFile();
        File file = new File(root, AndroidManifestBlock.FILE_NAME_BIN);
        if(!file.isFile()){
            file = new File(root, AndroidManifestBlock.FILE_NAME);
        }
        return file;
    }
    private File toResDirectory(File pubXmlFile){
        return pubXmlFile
                .getParentFile()
                .getParentFile();
    }
    private List<File> listAttrs(File pubXmlFile){
        return listValuesXml(pubXmlFile, "attr");
    }
    private List<File> listValuesXml(File pubXmlFile, String type){
        List<File> results = new ArrayList<>();
        File resDir = toResDirectory(pubXmlFile);
        List<File> valuesDirList = ApkUtil.listValuesDirectory(resDir);
        for(File valuesDir : valuesDirList){
            results.addAll(findValuesXml(valuesDir, type));
        }
        return results;
    }
    private List<File> findValuesXml(File valuesDir, String type){
        List<File> results = new ArrayList<>();
        File[] xmlFiles = valuesDir.listFiles();
        if(xmlFiles == null){
            return results;
        }
        for(File file : xmlFiles){
            if(!file.isFile()){
                continue;
            }
            String name = file.getName();
            if(!name.endsWith(".xml")){
                continue;
            }
            name = EncodeUtil.sanitizeType(name);
            if(name.equals(type)){
                results.add(file);
            }
        }
        return results;
    }
    private boolean isAlreadyParsed(File file){
        return parsedFiles.contains(file);
    }
    private void addParsedFiles(File file){
        parsedFiles.add(file);
    }

    public APKLogger getApkLogger() {
        return apkLogger;
    }
    public void setApkLogger(APKLogger logger) {
        this.apkLogger = logger;
        if(logger != null && apkModule.getApkLogger() == null){
            this.apkModule.setAPKLogger(logger);
        }
    }
    private void logMessage(String msg) {
        APKLogger apkLogger = getApkLogger();
        if(apkLogger!=null){
            apkLogger.logMessage(msg);
        }
    }
    private void logVerbose(String msg) {
        APKLogger apkLogger = getApkLogger();
        if(apkLogger!=null){
            apkLogger.logVerbose(msg);
        }
    }
}
