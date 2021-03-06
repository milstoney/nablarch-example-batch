package com.nablarch.example.app.batch.action;

import com.nablarch.example.app.batch.reader.FileCreateRequestReader;
import com.nablarch.example.app.entity.FileCreateRequest;
import com.nablarch.example.app.entity.FileData;
import nablarch.common.dao.UniversalDao;
import nablarch.core.beans.BeanUtil;
import nablarch.core.date.SystemTimeUtil;
import nablarch.core.repository.SystemRepository;
import nablarch.core.util.FileUtil;
import nablarch.core.util.annotation.Published;
import nablarch.fw.DataReader;
import nablarch.fw.ExecutionContext;
import nablarch.fw.Result;
import nablarch.fw.Result.Success;
import nablarch.fw.action.BatchAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * PDFファイル登録クラス。
 * @author Nabu Rakutaro
 *
 */
@Published
public class RegistrationPdfFileAction extends BatchAction<FileCreateRequest> {

    /** 設定キー：入力ファイルパス */
    private static final String FILE_PATH_KEY_INPUT = "RegistrationPdfFile.batch.input";
    /** 設定キー：作業ファイルパス */
    private static final String FILE_PATH_KEY_WORK = "RegistrationPdfFile.batch.work";

    @Override
    public DataReader<FileCreateRequest> createReader(ExecutionContext ctx) {
        return new FileCreateRequestReader();
    }

    @Override
    public Result handle(FileCreateRequest inputData, ExecutionContext ctx) {

        // 対象ファイル名取得
        String fileName = inputData.getFileName();
        // 入力Dirから作業Dirへファイルを移動
        File inputFile = new File(SystemRepository.getString(FILE_PATH_KEY_INPUT), fileName);
        File workFile = new File(SystemRepository.getString(FILE_PATH_KEY_WORK), fileName);
        FileUtil.move(inputFile, workFile);

        // 処理が単純で、応答時間が非常に短いので実務処理に近づけるためSleepさせる
        try {
            TimeUnit.MILLISECONDS.sleep(new Random().nextInt(20000));
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        // ファイルデータ登録
        FileData fileData = createFileData(inputData, fileName, workFile);
        UniversalDao.insert(fileData);

        // 登録済みのファイルを削除
        FileUtil.deleteFile(workFile);

        return new Success();
    }

    /**
     * {@link FileData}を生成する。
     *
     * @param inputData 入力データ
     * @param fileName ファイル名
     * @param workFile 一時ファイル
     * @return {@link FileData}
     */
    private FileData createFileData(final FileCreateRequest inputData, final String fileName, final File workFile) {
        Date sysDate = SystemTimeUtil.getDate();
        FileData fileData = new FileData();
        fileData.setFileDataId(inputData.getFileId());
        fileData.setFileName(fileName);
        fileData.setFileSize(workFile.length());
        fileData.setFileData(readFileToByte(workFile));
        fileData.setCreateTime(sysDate);
        return fileData;
    }

    /**
     * 正常終了時には、ステータスを処理済みに更新する。
     */
    @Override
    protected void transactionSuccess(final FileCreateRequest inputData, final ExecutionContext context) {
        updateStatus(inputData, "1");
    }

    /**
     * 異常終了時には、ステータスを異常終了に更新する。
     */
    @Override
    protected void transactionFailure(final FileCreateRequest inputData, final ExecutionContext context) {
        updateStatus(inputData, "2");
    }

    /**
     * FileCreateRequest のレコードのステータスを更新する。
     *
     * @param inputData 更新対象
     * @param status 更新するステータス
     */
    private static void updateStatus(final FileCreateRequest inputData, String status) {
        FileCreateRequest updateData = BeanUtil.createAndCopy(FileCreateRequest.class, inputData);
        updateData.setStatus(status);
        UniversalDao.update(updateData);
    }

    /**
     * ファイルを読み込み、その中身をバイト配列で取得する。
     *
     * @param file 対象ファイル
     * @return 読み込んだバイト配列
     * @throws RuntimeException ファイルが見つからない、アクセスできないときなど
     */
    byte[] readFileToByte(File file) {
        Path path = Paths.get(file.getAbsolutePath());
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
