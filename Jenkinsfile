@Library('shared-pipeline') _
import groovy.transform.Field

//应用名字；
@Field
String appName = 'demo-boot-app'

//spring boot应用的可执行jar包模块名；
@Field
String mainModuleName = 'controller'

//spring boot应用的HTTP端口号，请注意和其他应用区别开来；
@Field
String appPort = '7505'

//可选项，如果没有特殊需求，可注释掉
@Field
String JvmOptsProd='-Xmx256m -Xms256m'

//可选项，如果没有特殊需求，可注释掉
@Field
String JvmOptsFat='-Xmx256m -Xms256m'

//可选项，用于发送Jenkins发布通知；
//@Field
//String JenkinsNotificationBotUrl='https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=f9743577-b399-4989-acd8-516400860292&from=jenkinsfile'

//这一行之后不要动：
javaPipeline(this)
// javaPipelineWithJunit(this)