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
package com.reandroid.archive.writer;

import com.reandroid.apk.APKLogger;
import com.reandroid.archive.Archive;
import com.reandroid.archive.InputSource;
import com.reandroid.archive.ZipSignature;
import com.reandroid.archive.block.CentralEntryHeader;
import com.reandroid.archive.block.DataDescriptor;
import com.reandroid.archive.block.LocalFileHeader;
import com.reandroid.archive.io.CountingOutputStream;
import com.reandroid.utils.io.FileUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

class OutputSource {
    private final InputSource inputSource;
    private LocalFileHeader lfh;
    private EntryBuffer entryBuffer;
    private APKLogger apkLogger;

    OutputSource(InputSource inputSource){
        this.inputSource = inputSource;
    }
    void makeBuffer(BufferFileInput input, BufferFileOutput output) throws IOException {
        EntryBuffer entryBuffer = this.entryBuffer;
        if(entryBuffer != null){
            return;
        }
        entryBuffer = makeFromEntry();
        if(entryBuffer != null){
            this.entryBuffer = entryBuffer;
            return;
        }
        this.entryBuffer = writeBuffer(input, output);
    }
    private EntryBuffer writeBuffer(BufferFileInput input, BufferFileOutput output) throws IOException {
        long offset = output.position();
        writeBufferFile(output);
        long length = output.position() - offset;
        return new EntryBuffer(input, offset, length);
    }
    EntryBuffer makeFromEntry(){
        return null;
    }

    void writeApk(ApkWriter apkWriter) throws IOException{
        logLargeFileWrite();
        EntryBuffer entryBuffer = this.entryBuffer;
        FileChannel input = entryBuffer.getZipFileInput().getFileChannel();
        input.position(entryBuffer.getOffset());
        LocalFileHeader lfh = getLocalFileHeader();
        writeLFH(lfh, apkWriter);
        writeData(input, entryBuffer.getLength(), apkWriter);
        writeDD(lfh.getDataDescriptor(), apkWriter);
    }
    void writeCEH(ApkWriter apkWriter) throws IOException{
        LocalFileHeader lfh = getLocalFileHeader();
        CentralEntryHeader ceh = CentralEntryHeader.fromLocalFileHeader(lfh);
        ceh.writeBytes(apkWriter.getOutputStream());
    }
    private void writeLFH(LocalFileHeader lfh, ApkWriter apkWriter) throws IOException{
        ZipAligner aligner = apkWriter.getZipAligner();
        if(aligner != null){
            aligner.align(apkWriter.position(), lfh);
        }
        lfh.writeBytes(apkWriter.getOutputStream());
    }
    private void writeData(FileChannel input, long length, ApkWriter apkWriter) throws IOException{
        long offset = apkWriter.position();
        LocalFileHeader lfh = getLocalFileHeader();
        lfh.setFileOffset(offset);
        apkWriter.write(input, length);
    }
    void writeDD(DataDescriptor dataDescriptor, ApkWriter apkWriter) throws IOException{
        if(dataDescriptor == null){
            return;
        }
        dataDescriptor.writeBytes(apkWriter.getOutputStream());
    }
    private void writeBufferFile(BufferFileOutput output) throws IOException {
        LocalFileHeader lfh = getLocalFileHeader();

        InputSource inputSource = getInputSource();
        OutputStream rawStream = output.getOutputStream();

        CountingOutputStream<OutputStream> rawCounter = new CountingOutputStream<>(rawStream);
        CountingOutputStream<DeflaterOutputStream> deflateCounter = null;

        if(inputSource.getMethod() != Archive.STORED){
            DeflaterOutputStream deflaterInputStream =
                    new DeflaterOutputStream(rawCounter, new Deflater(Deflater.BEST_SPEED, true), true);
            deflateCounter = new CountingOutputStream<>(deflaterInputStream, false);
        }
        if(deflateCounter != null){
            rawCounter.disableCrc(true);
            inputSource.write(deflateCounter);
            deflateCounter.close();
            rawCounter.close();
        }else {
            inputSource.write(rawCounter);
        }

        lfh.setCompressedSize(rawCounter.getSize());

        if(deflateCounter != null){
            lfh.setMethod(Archive.DEFLATED);
            lfh.setCrc(deflateCounter.getCrc());
            lfh.setSize(deflateCounter.getSize());
        }else {
            lfh.setSize(rawCounter.getSize());
            lfh.setMethod(Archive.STORED);
            lfh.setCrc(rawCounter.getCrc());
        }

        inputSource.disposeInputSource();
    }

    InputSource getInputSource() {
        return inputSource;
    }
    LocalFileHeader getLocalFileHeader(){
        if(lfh == null){
            lfh = createLocalFileHeader();
            lfh.setFileName(getInputSource().getAlias());
            clearAlignment(lfh);
        }
        return lfh;
    }
    LocalFileHeader createLocalFileHeader(){
        InputSource inputSource = getInputSource();
        LocalFileHeader lfh = new LocalFileHeader();
        lfh.setSignature(ZipSignature.LOCAL_FILE);
        lfh.getGeneralPurposeFlag().initDefault();
        lfh.setFileName(inputSource.getAlias());
        lfh.setMethod(inputSource.getMethod());
        return lfh;
    }
    private void clearAlignment(LocalFileHeader lfh){
        lfh.getGeneralPurposeFlag().setHasDataDescriptor(false);
        lfh.setDataDescriptor(null);
        lfh.setZipAlign(0);
    }
    private void logLargeFileWrite(){
        APKLogger logger =  this.apkLogger;
        if(logger == null){
            return;
        }
        long size = getLocalFileHeader().getDataSize();
        if(size < LOG_LARGE_FILE_SIZE){
            return;
        }
        logFileWrite();
    }
    void logFileWrite(){
        APKLogger logger =  this.apkLogger;
        if(logger == null){
            return;
        }
        long size = getLocalFileHeader().getDataSize();
        logVerbose("Write [" + FileUtil.toReadableFileSize(size) + "] " +
                getInputSource().getAlias());
    }

    void setAPKLogger(APKLogger logger) {
        this.apkLogger = logger;
    }
    private void logVerbose(String msg) {
        if(apkLogger!=null){
            apkLogger.logVerbose(msg);
        }
    }
    private static final long LOG_LARGE_FILE_SIZE = 2L * 1000 * 1000 * 1024;

}
