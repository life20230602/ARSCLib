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
package com.reandroid.archive2;

import com.reandroid.apk.APKLogger;
import com.reandroid.archive.APKArchive;
import com.reandroid.archive.InputSource;
import com.reandroid.archive2.block.*;
import com.reandroid.archive2.io.ArchiveEntrySource;
import com.reandroid.archive2.io.ZipFileInput;
import com.reandroid.archive2.io.ArchiveUtil;
import com.reandroid.archive2.io.ZipInput;
import com.reandroid.archive2.model.LocalFileDirectory;
import com.reandroid.utils.collection.FilterIterator;
import com.reandroid.utils.io.FileUtil;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;

public class Archive implements Closeable {
    private final ZipInput zipInput;
    private final List<ArchiveEntry> entryList;
    private final EndRecord endRecord;
    private final ApkSignatureBlock apkSignatureBlock;
    public Archive(ZipInput zipInput) throws IOException {
        this.zipInput = zipInput;
        LocalFileDirectory lfd = new LocalFileDirectory();
        lfd.visit(zipInput);
        List<LocalFileHeader> localFileHeaderList = lfd.getHeaderList();
        List<CentralEntryHeader> centralEntryHeaderList = lfd.getCentralFileDirectory().getHeaderList();
        List<ArchiveEntry> entryList = new ArrayList<>(localFileHeaderList.size());
        for(int i=0;i<localFileHeaderList.size();i++){
            LocalFileHeader lfh = localFileHeaderList.get(i);
            CentralEntryHeader ceh = centralEntryHeaderList.get(i);
            ArchiveEntry archiveEntry = new ArchiveEntry(lfh, ceh);
            if(archiveEntry.isDirectory()){
                continue;
            }
            entryList.add(archiveEntry);
        }
        this.entryList  = entryList;
        this.endRecord = lfd.getCentralFileDirectory().getEndRecord();
        this.apkSignatureBlock = lfd.getApkSigBlock();
    }
    public Archive(File file) throws IOException {
        this(new ZipFileInput(file));
    }
    public APKArchive createAPKArchive(){
        return new APKArchive(mapEntrySource());
    }
    public Map<String, InputSource> mapEntrySource(){
        Map<String, InputSource> map = new LinkedHashMap<>();
        ZipInput zipInput = this.zipInput;
        List<ArchiveEntry> entryList = this.entryList;
        for(int i=0; i<entryList.size(); i++){
            ArchiveEntry entry = entryList.get(i);
            if(entry.isDirectory()){
                continue;
            }
            ArchiveEntrySource entrySource = new ArchiveEntrySource(zipInput, entry);
            map.put(entrySource.getAlias(), entrySource);
        }
        return map;
    }
    public ArchiveEntrySource getEntrySource(String path){
        if(path == null){
            return null;
        }
        ZipInput zipInput = this.zipInput;
        List<ArchiveEntry> entryList = this.entryList;
        for(int i=0; i<entryList.size(); i++){
            ArchiveEntry entry = entryList.get(i);
            if(path.equals(entry.getName())){
                return new ArchiveEntrySource(zipInput, entry);
            }
        }
        return null;
    }
    public List<String> listFilePaths(){
        List<ArchiveEntry> entryList = this.entryList;
        List<String> results = new ArrayList<>(entryList.size());
        for(int i=0; i<entryList.size(); i++){
            ArchiveEntry entry = entryList.get(i);
            if(entry.isDirectory()){
                continue;
            }
            results.add(entry.getName());
        }
        return results;
    }

    public boolean removeEntry(String path){
        if(path == null){
            return false;
        }
        List<ArchiveEntry> entryList = this.entryList;
        int index = -1;
        for(int i=0; i<entryList.size(); i++){
            ArchiveEntry entry = entryList.get(i);
            if(path.equals(entry.getName())){
                index = i;
                break;
            }
        }
        if(index != -1){
            entryList.remove(index);
            return true;
        }
        return false;
    }
    public void removeEntries(Collection<String> pathList){
        if(pathList == null){
            return;
        }
        List<ArchiveEntry> copy = new ArrayList<>(this.entryList);
        for(int i=0; i < copy.size(); i++){
            ArchiveEntry entry = copy.get(i);
            String path = entry.getName();
            if(pathList.contains(path)){
                pathList.remove(path);
                this.entryList.remove(entry);
            }
        }
    }
    public InputStream openRawInputStream(ArchiveEntry archiveEntry) throws IOException {
        return zipInput.getInputStream(archiveEntry.getFileOffset(), archiveEntry.getDataSize());
    }
    public InputStream openInputStream(ArchiveEntry archiveEntry) throws IOException {
        InputStream rawInputStream = openRawInputStream(archiveEntry);
        if(archiveEntry.getMethod() == ZipEntry.STORED){
            return rawInputStream;
        }
        return new InflaterInputStream(rawInputStream,
                new Inflater(true), 1024*1000);
    }
    public List<ArchiveEntry> getEntryList() {
        return entryList;
    }

    public ApkSignatureBlock getApkSignatureBlock() {
        return apkSignatureBlock;
    }
    public EndRecord getEndRecord() {
        return endRecord;
    }

    public int extractAll(File dir) throws IOException {
        return extractAll(dir, null, null);
    }
    public int extractAll(File dir, APKLogger logger) throws IOException {
        return extractAll(dir, null, logger);
    }
    public int extractAll(File dir, Predicate<ArchiveEntry> filter) throws IOException {
        return extractAll(dir, filter, null);
    }
    public int extractAll(File dir, Predicate<ArchiveEntry> filter, APKLogger logger) throws IOException {
        FilterIterator<ArchiveEntry> iterator =
                new FilterIterator<ArchiveEntry>(getEntryList().iterator(), filter){
                    @Override
                    public boolean test(ArchiveEntry archiveEntry){
                        return archiveEntry != null && !archiveEntry.isDirectory();
                    }
                };
        int result = 0;
        while (iterator.hasNext()){
            ArchiveEntry archiveEntry = iterator.next();
            extract(toFile(dir, archiveEntry), archiveEntry, logger);
            result ++;
        }
        return result;
    }
    public void extract(File file, ArchiveEntry archiveEntry) throws IOException{
        extract(file, archiveEntry, null);
    }
    public void extract(File file, ArchiveEntry archiveEntry, APKLogger logger) throws IOException{
        File parent = file.getParentFile();
        if(parent != null && !parent.exists()){
            parent.mkdirs();
        }
        if(logger != null){
            long size = archiveEntry.getDataSize();
            if(size > LOG_LARGE_FILE_SIZE){
                logger.logVerbose("Extracting ["
                        + FileUtil.toReadableFileSize(size) + "] "+ archiveEntry.getName());
            }
        }
        if(archiveEntry.getMethod() != ZipEntry.STORED){
            extractCompressed(file, archiveEntry);
        }else {
            extractStored(file, archiveEntry);
        }
    }
    private void extractStored(File file, ArchiveEntry archiveEntry) throws IOException {
        if(file.isFile()){
            file.delete();
        }
        file.createNewFile();
        StandardOpenOption openOption = StandardOpenOption.WRITE;
        FileChannel outputChannel = FileChannel.open(file.toPath(), openOption);
        FileChannel fileChannel = zipInput.getFileChannel();
        fileChannel.position(archiveEntry.getFileOffset());
        outputChannel.transferFrom(fileChannel, 0, archiveEntry.getDataSize());
        outputChannel.close();
    }
    private void extractCompressed(File file, ArchiveEntry archiveEntry) throws IOException {
        FileOutputStream outputStream = new FileOutputStream(file);
        ArchiveUtil.writeAll(openInputStream(archiveEntry), outputStream);
        outputStream.close();
    }
    private File toFile(File dir, ArchiveEntry archiveEntry){
        String name = archiveEntry.getName().replace('/', File.separatorChar);
        return new File(dir, name);
    }
    @Override
    public void close() throws IOException {
        this.zipInput.close();
    }
    private static final long LOG_LARGE_FILE_SIZE = 1024 * 1000 * 20;
}
