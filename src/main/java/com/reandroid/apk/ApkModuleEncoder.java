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
package com.reandroid.apk;

import com.reandroid.archive.ZipEntryMap;
import com.reandroid.archive.FileInputSource;
import com.reandroid.archive.InputSource;
import com.reandroid.archive.block.ApkSignatureBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class ApkModuleEncoder extends ApkModuleCoder{
    private DexEncoder mDexEncoder;
    public ApkModuleEncoder(){
        super();
    }

    public void scanDirectory(File mainDirectory) throws IOException{
        logMessage("Scanning: " + mainDirectory.getName());
        encodeBinaryManifest(mainDirectory);
        loadUncompressedFiles(mainDirectory);
        buildResources(mainDirectory);
        encodeDexFiles(mainDirectory);
        scanRootDir(mainDirectory);
        restorePathMap(mainDirectory);
        restoreSignatures(mainDirectory);
        sortFiles();
        refreshTable();
    }
    public void encodeBinaryManifest(File mainDirectory){
        File file = new File(mainDirectory, AndroidManifestBlock.FILE_NAME_BIN);
        if(!file.isFile()){
            return;
        }
        logMessage("Using binary xml: " + file.getName());
        FileInputSource inputSource = new FileInputSource(file, AndroidManifestBlock.FILE_NAME);
        getApkModule().add(inputSource);
    }
    public abstract void buildResources(File mainDirectory) throws IOException;
    @Override
    public abstract ApkModule getApkModule();


    private void refreshTable(){
        logMessage("Refreshing resource table ...");
        getApkModule().refreshTable();
        logMessage(getApkModule().getTableBlock().toString());
    }
    private void sortFiles(){
        logMessage("Sorting files ...");
        ZipEntryMap archive = getApkModule().getZipEntryMap();
        archive.autoSortApkFiles();
    }
    private void restoreSignatures(File mainDirectory) throws IOException {
        File sigDir = new File(mainDirectory, SIGNATURE_DIRECTORY_NAME);
        if(!sigDir.isDirectory()){
            return;
        }
        logMessage("Loading signatures ...");
        ApkModule apkModule = getApkModule();
        ApkSignatureBlock signatureBlock = new ApkSignatureBlock();
        signatureBlock.scanSplitFiles(sigDir);
        apkModule.setApkSignatureBlock(signatureBlock);
    }
    private void restorePathMap(File mainDirectory) throws IOException{
        File file = new File(mainDirectory, PathMap.JSON_FILE);
        if(!file.isFile()){
            return;
        }
        logMessage("Restoring original file paths ...");
        PathMap pathMap = new PathMap();
        JSONArray jsonArray = new JSONArray(file);
        pathMap.fromJson(jsonArray);
        pathMap.restore(getApkModule());
    }
    private void scanRootDir(File mainDirectory){
        logMessage("Scanning root directory ...");
        File root = new File(mainDirectory, ROOT_DIRECTORY_NAME);
        ZipEntryMap archive = getApkModule().getZipEntryMap();
        List<File> rootFileList = ApkUtil.recursiveFiles(root);
        for(File file:rootFileList){
            String path = ApkUtil.toArchivePath(root, file);
            FileInputSource inputSource = new FileInputSource(file, path);
            archive.add(inputSource);
        }
    }
    public void encodeDexFiles(File mainDirectory) throws IOException {
        logMessage("Building dex ...");
        List<InputSource> dexList = getRawDexEncoder()
                .buildDexFiles(this, mainDirectory);
        ApkModule apkModule = getApkModule();
        apkModule.addAll(dexList);
        DexEncoder dexEncoder = getDexEncoder();
        if(dexEncoder != null){
            dexList = dexEncoder.buildDexFiles(this, mainDirectory);
            apkModule.addAll(dexList);
        }
    }

    public DexEncoder getRawDexEncoder() {
        DexFileRawEncoder dexFileRawEncoder = new DexFileRawEncoder();
        dexFileRawEncoder.setApkLogger(getApkLogger());
        return dexFileRawEncoder;
    }
    public DexEncoder getDexEncoder() {
        return mDexEncoder;
    }
    public void setDexEncoder(DexEncoder dexEncoder) {
        this.mDexEncoder = dexEncoder;
    }

    void loadUncompressedFiles(File mainDirectory) throws IOException {
        File file = new File(mainDirectory, UncompressedFiles.JSON_FILE);
        UncompressedFiles uncompressedFiles = getApkModule().getUncompressedFiles();
        uncompressedFiles.fromJson(file);
    }

}
