# demo-app

Demo App，所有的Java web类型的应用都应该基于本项目进行搭建。

## 主要功能
1. 集成了miniso-boot，对CAT/Apollo/Dubbo等中间件通过auto-configuration的方式引入；
2. 集成了Jenkins，每个应用都需要有各自的Jenkinsfile，以此才能实现Jenkins发布；
3. web应用三层架构：DAO/Service/Controller；
4. settings.miniso.xml是海外电商技术部的maven配置文件，大家需要使用这个文件来访问我们内部的maven仓库。