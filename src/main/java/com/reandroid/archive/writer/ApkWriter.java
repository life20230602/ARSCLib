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
import com.reandroid.archive.RenamedInputSource;
import com.reandroid.archive.InputSource;
import com.reandroid.archive.WriteProgress;
import com.reandroid.archive.ZipSignature;
import com.reandroid.archive.block.*;
import com.reandroid.archive.io.ArchiveFileEntrySource;
import com.reandroid.archive.io.ZipFileOutput;
import com.reandroid.arsc.chunk.TableBlock;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class ApkWriter extends ZipFileOutput {
    private final Object mLock = new Object();
    private final InputSource[] sourceList;
    private ZipAligner zipAligner;
    private ApkSignatureBlock apkSignatureBlock;
    private APKLogger apkLogger;
    private WriteProgress writeProgress;

    public ApkWriter(File file, InputSource[] sourceList) throws IOException {
        super(file);
        this.sourceList = sourceList;
        this.zipAligner = ZipAligner.apkAligner();
    }
    public void write()throws IOException {
        synchronized (mLock){
            OutputSource[] outputList = buildOutputEntries();
            logMessage("Buffering compress changed files ...");
            BufferFileInput buffer = writeBuffer(outputList);
            buffer.unlock();
            if(getZipAligner() != null){
                logMessage("Zip align ON");
            }
            writeApk(outputList);
            buffer.close();

            writeSignatureBlock();

            writeCEH(outputList);
            this.close();
        }
    }
    public void setApkSignatureBlock(ApkSignatureBlock apkSignatureBlock) {
        this.apkSignatureBlock = apkSignatureBlock;
    }
    public ZipAligner getZipAligner() {
        return zipAligner;
    }
    public void setZipAligner(ZipAligner zipAligner) {
        this.zipAligner = zipAligner;
    }

    private void writeCEH(OutputSource[] outputList) throws IOException{
        EndRecord endRecord = new EndRecord();
        endRecord.setSignature(ZipSignature.END_RECORD);
        long offset = position();
        endRecord.setOffsetOfCentralDirectory(offset);
        int count = outputList.length;
        endRecord.setNumberOfDirectories(count);
        endRecord.setTotalNumberOfDirectories(count);
        for(int i = 0; i < count; i++){
            OutputSource outputSource = outputList[i];
            outputSource.writeCEH(this);
        }
        long cedLength = position() - offset;
        endRecord.setLengthOfCentralDirectory(cedLength);
        OutputStream outputStream = getOutputStream();
        Zip64Record zip64Record = endRecord.getZip64Record();
        if(zip64Record != null){
            long offsetOfRecord = position();
            logMessage("ZIP64: " + zip64Record);
            zip64Record.writeBytes(outputStream);
            Zip64Locator zip64Locator = endRecord.getZip64Locator();
            zip64Locator.setOffsetZip64Record(offsetOfRecord);
            logMessage("ZIP64: " + zip64Locator);
            zip64Locator.writeBytes(outputStream);
        }
        endRecord.writeBytes(getOutputStream());
    }
    private void writeApk(OutputSource[] outputList) throws IOException{
        int length = outputList.length;
        logMessage("Writing files: " + length);
        APKLogger logger = this.apkLogger;
        for(int i = 0; i < length; i++){
            OutputSource outputSource = outputList[i];
            outputSource.setAPKLogger(logger);
            outputSource.writeApk( this);
            if(i % 100 == 0){
                outputSource.logFileWrite();
            }
        }
    }
    private void writeSignatureBlock() throws IOException {
        ApkSignatureBlock signatureBlock = this.apkSignatureBlock;
        if(signatureBlock == null){
            return;
        }
        logMessage("Writing signature block ...");
        long offset = position();
        if(ZipHeader.isZip64Length(offset)){
            logMessage("ZIP64 mode, skip writing signature block!");
            return;
        }
        int alignment = 4096;
        int filesPadding = (int) ((alignment - (offset % alignment)) % alignment);
        OutputStream outputStream = getOutputStream();
        if(filesPadding > 0){
            outputStream.write(new byte[filesPadding]);
        }
        signatureBlock.updatePadding();
        signatureBlock.writeBytes(outputStream);
    }
    private BufferFileInput writeBuffer(OutputSource[] outputList) throws IOException {
        File bufferFile = getBufferFile();
        BufferFileOutput output = new BufferFileOutput(bufferFile);
        BufferFileInput input = new BufferFileInput(bufferFile);
        OutputSource tableSource = null;
        int length = outputList.length;
        for(int i = 0; i < length; i++){
            OutputSource outputSource = outputList[i];
            InputSource inputSource = outputSource.getInputSource();
            if(tableSource == null && TableBlock.FILE_NAME.equals(inputSource.getAlias())){
                tableSource = outputSource;
                continue;
            }
            onCompressFileProgress(inputSource.getAlias(),
                    inputSource.getMethod(),
                    output.position());
            outputSource.makeBuffer(input, output);
        }
        if(tableSource != null){
            tableSource.makeBuffer(input, output);
        }
        output.close();
        return input;
    }
    private File getBufferFile(){
        File file = getFile();
        File dir = file.getParentFile();
        String name = file.getAbsolutePath();
        name = "tmp" + name.hashCode();
        File bufFile;
        if(dir != null){
            bufFile = new File(dir, name);
        }else {
            bufFile = new File(name);
        }
        bufFile.deleteOnExit();
        return bufFile;
    }
    private OutputSource[] buildOutputEntries(){
        InputSource[] sourceList = this.sourceList;
        int length = sourceList.length;
        OutputSource[] results = new OutputSource[length];
        for(int i = 0; i < length; i++){
            InputSource inputSource = sourceList[i];
            results[i] = toOutputSource(inputSource);
        }
        return results;
    }
    private OutputSource toOutputSource(InputSource inputSource){
        if(inputSource instanceof ArchiveFileEntrySource){
            return new ArchiveOutputSource(inputSource);
        }
        if(inputSource instanceof RenamedInputSource){
            RenamedInputSource<?> renamedInputSource = ((RenamedInputSource<?>) inputSource);
            if(renamedInputSource.getParentInputSource(ArchiveFileEntrySource.class) != null){
                return new RenamedArchiveSource(renamedInputSource);
            }
        }
        return new OutputSource(inputSource);
    }

    public void setWriteProgress(WriteProgress writeProgress){
        this.writeProgress = writeProgress;
    }

    private void onCompressFileProgress(String path, int mode, long writtenBytes) {
        if(writeProgress!=null){
            writeProgress.onCompressFile(path, mode, writtenBytes);
        }
    }

    APKLogger getApkLogger(){
        return apkLogger;
    }
    public void setAPKLogger(APKLogger logger) {
        this.apkLogger = logger;
    }
    private void logMessage(String msg) {
        if(apkLogger!=null){
            apkLogger.logMessage(msg);
        }
    }
    private void logError(String msg, Throwable tr) {
        if(apkLogger!=null){
            apkLogger.logError(msg, tr);
        }
    }
    private void logVerbose(String msg) {
        if(apkLogger!=null){
            apkLogger.logVerbose(msg);
        }
    }

}
