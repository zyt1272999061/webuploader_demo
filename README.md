# SpringBoot 2.0 整合 WebUploader插件

## 一、项目介绍

​	本项目基于SpringBoot2.0构建，使用Thymeleaf视图解析器，前端使用bootstrap+WebUploader

## 二、技术介绍

* springboot官网：https://spring.io/projects/spring-boot/
* thymeleaf官网：https://www.thymeleaf.org/
* webuploader官网：https://fex.baidu.com/webuploader/
* bootstrap官网：https://www.bootcss.com/

## 三、页面效果

1. 下载项目，启动并访问：http://localhost:8080/upload/index 

2. 页面效果

   - 首页

     ![首页](https://github.com/zyt1272999061/webuploader_demo/blob/master/images/index.bmp)

   - 选择文件

     ![等待上传](https://github.com/zyt1272999061/webuploader_demo/blob/master/images/paused.bmp)

   - 开始上传/暂停上传

     ![暂停上传](https://github.com/zyt1272999061/webuploader_demo/blob/master/images/waiting.bmp)

## 四、技术实现

~~~flow
```flow
st=>start: 开始
op=>operation: 选择文件
op1=>operation: 将文件分片并上传每个分片到服务器
op2=>operation: 所有分片上传成功后，通知服务器合并分片
e=>end
st->op->op1->op2()->e
&```
~~~

## 五、代码实现

1. pom.xml

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion>4.0.0</modelVersion>
       <parent>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-starter-parent</artifactId>
           <version>2.1.6.RELEASE</version>
           <relativePath/> <!-- lookup parent from repository -->
       </parent>
       <groupId>com.github.zyt</groupId>
       <artifactId>webuploader</artifactId>
       <version>0.0.1-SNAPSHOT</version>
       <name>webuploader</name>
       <description>Demo project for Spring Boot</description>
   
       <properties>
           <java.version>1.8</java.version>
       </properties>
   
       <dependencies>
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-web</artifactId>
           </dependency>
           <!--thymeleaf相关-->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-thymeleaf</artifactId>
           </dependency>
           <!--热部署相关-->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-devtools</artifactId>
               <scope>runtime</scope>
           </dependency>
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-test</artifactId>
               <scope>test</scope>
           </dependency>
       </dependencies>
   
       <build>
           <plugins>
               <plugin>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-maven-plugin</artifactId>
               </plugin>
               <!--热部署相关-->
               <plugin>
                   <groupId>org.springframework.boot</groupId>
                   <artifactId>spring-boot-maven-plugin</artifactId>
                   <configuration>
                       <!-- 如果没有该配置，热部署的devtools不生效 -->
                       <fork>true</fork>
                   </configuration>
               </plugin>
   
           </plugins>
       </build>
   </project>
   ```

   

2. application.properties

   ```properties
   # thymeleaf
   spring.thymeleaf.prefix=classpath:/templates/
   spring.thymeleaf.suffix=.html
   spring.thymeleaf.mode=HTML
   spring.thymeleaf.encoding=UTF-8
   spring.thymeleaf.servlet.content-type=text/html
   spring.thymeleaf.cache=false
   #开启静态资源扫描
   spring.mvc.static-path-pattern=/**
   #开启热部署
   spring.devtools.restart.enabled=true
   #设置最大上传大小
   spring.servlet.multipart.enabled=true
   spring.servlet.multipart.max-file-size=5GB
   spring.servlet.multipart.max-request-size=5GB
   ```

   

3. 上传页面

   * 引入css、js

     ```html
     <link rel="stylesheet" th:href="@{/webuploader/webuploader.css}"/>
     <!-- 最新版本的 Bootstrap 核心 CSS 文件 -->
     <link rel="stylesheet" th:href="@{/bootstrap/css/bootstrap.min.css}">
     <!-- 最新的 Bootstrap 核心 JavaScript 文件 -->
     <script type="application/javascript" th:src="@{/bootstrap/js/bootstrap.min.js}"></script>
     <script type="application/javascript" th:src="@{/js/jquery-3.4.1.min.js}"></script>
     <script type="application/javascript" th:src="@{/webuploader/webuploader.js}">
     ```

     

   * 页面HTML

     ```html
     <div class="container">
         <div class="row" style="margin-top: 20px;">
             <div class="col-md-12">
                 <div class="panel panel-default">
                     <div class="panel-heading">
                         <h3 class="panel-title">上传</h3>
                     </div>
                     <div class="panel-body">
                         <div id="uploader" class="wu-example">
                             <!--用来存放文件信息-->
                             <div id="thelist" class="uploader-list"></div>
                             <div class="btns">
                                 <div id="picker">选择文件</div>
                                 <button id="ctlBtn" class="btn btn-default">开始上传</button>
                             </div>
                         </div>
                     </div>
                 </div>
     
             </div>
         </div>
     </div>
     ```

     

   * javascript

     ```javascript
     <script th:inline="javascript" type="application/javascript">
         /*<![CDATA[*/
         var $ = jQuery,
             $list = $('#thelist'),
             $btn = $('#ctlBtn'),
             state = 'pending',
             uploader;
     var fileMd5;//文件的MD5值
     var fileName;//文件名称
     var blockSize = 10 * 1024 * 1024;
     var md5Arr = new Array(); //文件MD5数组
     var timeArr = new Array();//文件上传时间戳数组
     WebUploader.Uploader.register({
         "before-send-file": "beforeSendFile",//整个文件上传前
         "before-send": "beforeSend",//每个分片上传前
         "after-send-file": "afterSendFile"//分片上传完毕
     }, {
         //1.生成整个文件的MD5值
         beforeSendFile: function (file) {
             var index = file.id.slice(8);//文件下标
             var startTime = new Date();//一个文件上传初始化时，开始计时
             timeArr[index] = startTime;//将每一个文件初始化时的时间放入时间数组
             var deferred = WebUploader.Deferred();
             //计算文件的唯一标记fileMd5，用于断点续传  如果.md5File(file)方法里只写一个file参数则计算MD5值会很慢 所以加了后面的参数：10*1024*1024
             (new WebUploader.Uploader())
                 .md5File(file, 0, blockSize)
                 .progress(function (percentage) {
                 $('#' + file.id).find('p.state').text('正在读取文件信息...');
             })
                 .then(function (value) {
                 $("#" + file.id).find('p.state').text('成功获取文件信息...');
                 fileMd5 = value;
                 var index = file.id.slice(8);
                 md5Arr[index] = fileMd5;//将文件的MD5值放入数组，以便分片合并时能够取到当前文件对应的MD5
                 uploader.options.formData.guid = fileMd5;//全局的MD5
                 deferred.resolve();
             });
             fileName = file.name;
             return deferred.promise();
         },
         //2.如果有分快上传，则每个分块上传前调用此函数
         beforeSend: function (block) {
             var deferred = WebUploader.Deferred();
             $.ajax({
                 type: "POST",
                 url: /*[[@{/upload/checkblock}]]*/, //ajax验证每一个分片
                 data: {
                 //fileName: fileName,
                 //fileMd5: fileMd5, //文件唯一标记
                 chunk: block.chunk, //当前分块下标
                 chunkSize: block.end - block.start,//当前分块大小
                 guid: uploader.options.formData.guid,
             },
                    cache: false,
                    async: false, // 与js同步
                    timeout: 1000, // 超时的话，只能认为该分片未上传过
                    dataType: "json",
                    success: function (response) {
                 if (response.ifExist) {
                     //分块存在，跳过
                     deferred.reject();
                 } else {
                     //分块不存在或不完整，重新发送该分块内容
                     deferred.resolve();
                 }
             }
         });
         this.owner.options.formData.fileMd5 = fileMd5;
         deferred.resolve();
     return deferred.promise();
     },
         //3.当前所有的分块上传成功后调用此函数
         afterSendFile: function (file) {
             //如果分块全部上传成功，则通知后台合并分块
             var index = file.id.slice(8);//获取文件的下标
             $('#' + file.id).find('p.state').text('已上传');
             $.post(/*[[@{/upload/combine}]]*/, {"guid": md5Arr[index], fileName: file.name},
                 function (data) {
                 }, "json");
     }
     });
     
     //上传方法
     uploader = WebUploader.create({
         // swf文件路径
         swf: '@{webuploader/Uploader.swf}',
         // 文件接收服务端。
         server: /*[[@{/upload/save}]]*/,
         // 选择文件的按钮。可选。
         // 内部根据当前运行是创建，可能是input元素，也可能是flash.
         pick: '#picker',
         chunked: true, //分片处理
         chunkSize: 10 * 1024 * 1024, //每片5M
         threads: 3,//上传并发数。允许同时最大上传进程数。
         // 不压缩image, 默认如果是jpeg，文件上传前会压缩一把再上传！
         resize: false
     });
     // 当有文件被添加进队列的时候
     uploader.on('fileQueued', function (file) {
         //文件列表添加文件页面样式
         $list.append('<div id="' + file.id + '" class="item">' +
                      '<div class="row">\n' +
                      '<div class="col-md-11"><h4 class="info">' + file.name + '</h4></div>\n' +
                      '<div class="col-md-1"><button class="btn btn-info delbtn" onclick="delFile(\'' + file.id + '\')">删除</button></div>\n' +
                      '</div>\n' +
                      '<div class="row">\n' +
                      '<div class="col-md-5"><p class="state">等待上传...</p></div>\n' +
                      '<div class="col-md-7"><span class="time"></span></div>\n' +
                      '</div>');
     });
     // 文件上传过程中创建进度条实时显示
     uploader.on('uploadProgress', function (file, percentage) {
         //计算每个分块上传完后还需多少时间
         var index = file.id.slice(8);//文件的下标
         var currentTime = new Date();
         var timeDiff = currentTime.getTime() - timeArr[index].getTime();//获取已用多少时间
         var timeStr;
         //如果percentage==1说明已经全部上传完毕，则需更改页面显示
         if (1 == percentage) {
             timeStr = "上传用时：" + countTime(timeDiff);//计算总用时
         } else {
             timeStr = "预计剩余时间：" + countTime(timeDiff / percentage * (1 - percentage));//估算剩余用时
         }
         //创建进度条
         var $li = $('#' + file.id), $percent = $li.find('.progress .progress-bar');
         // 避免重复创建
         if (!$percent.length) {
             $percent = $(
                 '<div class="progress progress-striped active">'
                 + '<div class="progress-bar" role="progressbar" style="width: 0%">'
                 + '</div>' + '</div>')
                 .appendTo($li).find('.progress-bar');
         }
         $li.find('p.state').text('上传中');
         $li.find('span.time').text(timeStr);
         $percent.css('width', percentage * 100 + '%');
     });
     /*    uploader.on('uploadSuccess', function (file) {
                 var index = file.id.slice(8);
                 $('#' + file.id).find('p.state').text('已上传');
                 $.post(/!*[[@{/upload/combine}]]*!/, {
                     "guid": md5Arr[index],
                     fileName: file.name,
                 }, function () {
                     uploader.removeFile(file);
                 }, "json");
             });*/
     
     //上传失败时
     uploader.on('uploadError', function (file) {
         $('#' + file.id).find('p.state').text('上传出错');
     });
     //上传完成时
     uploader.on('uploadComplete', function (file) {
         $('#' + file.id).find('.progress').fadeOut();
     });
     //上传状态
     uploader.on('all', function (type) {
         if (type === 'startUpload') {
             state = 'uploading';
         } else if (type === 'stopUpload') {
             state = 'paused';
         } else if (type === 'uploadFinished') {
             state = 'done';
         }
         if (state === 'uploading') {
             $btn.text('暂停上传');
         } else {
             $btn.text('开始上传');
         }
     });
     //开始上传，暂停上传的函数
     $btn.on('click', function () {
         //每个文件的删除按钮不可用
         $(".delbtn").attr("disabled", true);
         if (state === 'uploading') {
             uploader.stop(true);//暂停
             //删除按钮可用
             $(".delbtn").removeAttr("disabled");
         } else {
             uploader.upload();
         }
     });
     //删除文件
     function delFile(id) {
         //将文件从uploader的文件列表中删除
         uploader.removeFile(uploader.getFile(id, true));
         //清除页面元素
         $("#" + id).remove();
     }
     //获取上传时还需多少时间
     function countTime(date) {
         var str = "";
         //计算出相差天数
         var days = Math.floor(date / (24 * 3600 * 1000))
         if (days > 0) {
             str += days + " 天 ";
         }
         //计算出小时数
         var leave1 = date % (24 * 3600 * 1000) //计算天数后剩余的毫秒数
         var hours = Math.floor(leave1 / (3600 * 1000))
         if (hours > 0) {
             str += hours + " 小时 ";
         }
         //计算相差分钟数
         var leave2 = leave1 % (3600 * 1000) //计算小时数后剩余的毫秒数
         var minutes = Math.floor(leave2 / (60 * 1000))
         if (minutes > 0) {
             str += minutes + " 分 ";
         }
         //计算相差秒数
         var leave3 = leave2 % (60 * 1000) //计算分钟数后剩余的毫秒数
         var seconds = Math.round(leave3 / 1000)
         if (seconds > 0) {
             str += seconds + " 秒 ";
         } else {
             /* str += parseInt(date) + " 毫秒"; */
             str += " < 1 秒";
         }
         return str;
     }
     /*]]>*/
     </script>
     ```

   

4. 后端代码

   * 校验

     ```Java
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
     ```

     

   * 上传分片

     ```java
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
     ```

     

   * 合并分片

   ```java
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
           logger.info("合并文件——开始 [ 文件名称：" + fileName + " ，MD5值：" + guid + " ]");
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
               logger.info("文件合并——结束 [ 文件名称：" + fileName + " ，MD5值：" + guid + " ]");
           }
       } catch (Exception e) {
           logger.error("文件合并——失败 " + e.getMessage());
       }
   }
   ```