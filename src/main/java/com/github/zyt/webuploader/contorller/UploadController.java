package com.github.zyt.webuploader.contorller;

import com.github.zyt.webuploader.entity.FileEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 分片上传Controller
 */
@Controller
@RequestMapping("upload")
@Slf4j
public class UploadController {


    /**
     * 上传路径
     */
    @Value("${upload_path}")
    private String uploadPath;

    /**
     * 跳转到首页
     *
     * @return
     */
    @GetMapping("index")
    public ModelAndView toUpload() {
        ModelAndView mav = new ModelAndView("upload");
        mav.addObject("uploadPath", uploadPath + File.separator + "real");
        return mav;
    }

    /**
     * 查看当前分片是否上传
     *
     * @param request
     * @param response
     */
    @PostMapping("checkblock")
    @ResponseBody
    public void checkMd5(HttpServletRequest request, HttpServletResponse response) {
        //当前分片
        String chunk = request.getParameter("chunk");
        //分片大小
        String chunkSize = request.getParameter("chunkSize");
        //当前文件的MD5值
        String guid = request.getParameter("guid");
        //分片上传路径
        String tempPath = uploadPath + File.separator + "temp";
        File checkFile = new File(tempPath + File.separator + guid + File.separator + chunk);
        response.setContentType("text/html;charset=utf-8");
        try {
            //如果当前分片存在，并且长度等于上传的大小
            if (checkFile.exists() && checkFile.length() == Integer.parseInt(chunkSize)) {
                response.getWriter().write("{\"ifExist\":1}");
            } else {
                response.getWriter().write("{\"ifExist\":0}");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 上传分片
     *
     * @param file
     * @param chunk
     * @param guid
     * @throws IOException
     */
    @PostMapping("save")
    @ResponseBody
    public void upload(@RequestParam MultipartFile file, Integer chunk, String guid) throws IOException {
        String filePath = uploadPath + File.separator + "temp" + File.separator + guid;
        File tempfile = new File(filePath);
        if (!tempfile.exists()) {
            tempfile.mkdirs();
        }
        RandomAccessFile raFile = null;
        BufferedInputStream inputStream = null;
        if (chunk == null) {
            chunk = 0;
        }
        try {
            File dirFile = new File(filePath, String.valueOf(chunk));
            //以读写的方式打开目标文件
            raFile = new RandomAccessFile(dirFile, "rw");
            raFile.seek(raFile.length());
            inputStream = new BufferedInputStream(file.getInputStream());
            byte[] buf = new byte[1024];
            int length = 0;
            while ((length = inputStream.read(buf)) != -1) {
                raFile.write(buf, 0, length);
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (raFile != null) {
                raFile.close();
            }
        }
    }

    /**
     * 合并文件
     *
     * @param guid
     * @param fileName
     */
    @PostMapping("combine")
    @ResponseBody
    public void combineBlock(String guid, String fileName) {
        //分片文件临时目录
        File tempPath = new File(uploadPath + File.separator + "temp" + File.separator + guid);
        //真实上传路径
        File realPath = new File(uploadPath + File.separator + "real");
        if (!realPath.exists()) {
            realPath.mkdirs();
        }
        File realFile = new File(uploadPath + File.separator + "real" + File.separator + fileName);
        FileOutputStream os = null;// 文件追加写入
        FileChannel fcin = null;
        FileChannel fcout = null;
        try {
            log.info("合并文件——开始 [ 文件名称：" + fileName + " ，MD5值：" + guid + " ]");
            os = new FileOutputStream(realFile, true);
            fcout = os.getChannel();
            if (tempPath.exists()) {
                //获取临时目录下的所有文件
                File[] tempFiles = tempPath.listFiles();
                //按名称排序
                Arrays.sort(tempFiles, (o1, o2) -> {
                    if (Integer.parseInt(o1.getName()) < Integer.parseInt(o2.getName())) {
                        return -1;
                    }
                    if (Integer.parseInt(o1.getName()) == Integer.parseInt(o2.getName())) {
                        return 0;
                    }
                    return 1;
                });
                //每次读取10MB大小，字节读取
                //byte[] byt = new byte[10 * 1024 * 1024];
                //int len;
                //设置缓冲区为10MB
                ByteBuffer buffer = ByteBuffer.allocate(10 * 1024 * 1024);
                for (int i = 0; i < tempFiles.length; i++) {
                    FileInputStream fis = new FileInputStream(tempFiles[i]);
                    /*while ((len = fis.read(byt)) != -1) {
                        os.write(byt, 0, len);
                    }*/
                    fcin = fis.getChannel();
                    if (fcin.read(buffer) != -1) {
                        buffer.flip();
                        while (buffer.hasRemaining()) {
                            fcout.write(buffer);
                        }
                    }
                    buffer.clear();
                    fis.close();
                    //删除分片
                    tempFiles[i].delete();
                }
                os.close();
                //删除临时目录
                if (tempPath.isDirectory() && tempPath.exists()) {
                    System.gc(); // 回收资源
                    tempPath.delete();
                }
                log.info("文件合并——结束 [ 文件名称：" + fileName + " ，MD5值：" + guid + " ]");
            }
        } catch (Exception e) {
            log.error("文件合并——失败 " + e.getMessage());
        }
    }

    /**
     * 查询上传目录下的全部文件
     *
     * @return
     */
    @GetMapping("/getFiles")
    @ResponseBody
    public Map getFiles() {
        Map map = new HashMap();
        String realUploadPath = uploadPath + File.separator + "real";
        File directory = new File(realUploadPath);
        File[] files = directory.listFiles();
        List<FileEntity> fileList = new ArrayList<>();
        if (null != files && files.length > 0) {
            for (File file : files) {
                fileList.add(new FileEntity(file.getName(), getDate(file.lastModified()), file.getName().substring(file.getName().lastIndexOf(".")), Math.round(file.length() / 1024) + "KB"));
            }
        }
        map.put("fileList", fileList);
        return map;

    }

    /**
     * Long 转 Date
     *
     * @param time
     * @return
     */
    private String getDate(Long time) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
        Date date = new Date(time);
        return sdf.format(date);
    }

    /**
     * 文件下载
     *
     * @param fileName 文件名称
     * @param response HttpServletResponse
     */
    @GetMapping("downloadFile")
    @ResponseBody
    public void downLoadFile(String fileName, HttpServletResponse response) {
        File file = new File(uploadPath + File.separator + "real" + File.separator + fileName);
        if (file.exists()) {
            InputStream is = null;
            OutputStream os = null;
            try {
                response.reset();
                // 设置强制下载不打开
                response.setContentType("application/force-download");
                //设置下载文件名
                response.addHeader("Content-Disposition", "attachment;filename=" + fileName);
                response.addHeader("Content-Length", "" + file.length());
                //定义输入输出流
                os = new BufferedOutputStream(response.getOutputStream());
                is = new BufferedInputStream(new FileInputStream(file));
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                    os.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    is.close();
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                log.info("文件下载成功——文件名：" + fileName);
            }
        }
    }

    /**
     * 删除文件
     *
     * @param fileName
     * @return
     */
    @GetMapping("/delFile")
    @ResponseBody
    public Map delFile(String fileName) {
        boolean b = false;
        File file = new File(uploadPath + File.separator + "real" + File.separator + fileName);
        if (file.exists() && file.isFile()) {
            b = file.delete();
        }
        Map map = new HashMap();
        map.put("result", b + "");
        return map;
    }
}
