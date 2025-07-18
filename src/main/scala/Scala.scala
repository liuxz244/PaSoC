package scala

import scala.sys.process._
import scala.io.Source
import java.io.{File, PrintWriter}

object FilePrepender {
    
    def prependLine(filename: String, line: String): Unit = {
        // 读取文件内容
        val fileContent = Source.fromFile(filename).getLines().toList
        // 添加新行到文件内容的开头
        val newContent = line :: fileContent
        // 写回文件
        val writer = new PrintWriter(filename)
        newContent.foreach(writer.println)
        writer.close()
    }

    def convertReadmemhPathsToWindows(svFile: String): Unit = {
        val pattern = """\$readmemh\("([^"]+)",\s*([^)]+)\);""".r
        val lines = Source.fromFile(svFile).getLines().toList
        val newLines = lines.map { line =>
            pattern.findFirstMatchIn(line) match {
                case Some(m) =>
                    val origPath = m.group(1)
                    // 先转为绝对路径
                    val absPath = new File(origPath).getAbsolutePath
                    // WSL路径转win路径字符串处理
                    var winPath = absPath
                    .replaceFirst("^/mnt/([a-zA-Z])/", "$1:/")
                    .replace("/", "\\\\")
                    // 盘符首字母大写  (如 d:\foo -> D:\foo)
                    if (winPath.matches("^[a-zA-Z]:.*")) {
                    winPath = winPath.substring(0,1).toUpperCase + winPath.substring(1)
                    }
                    line.replace(origPath, winPath)
                case None => line
            }
        }
        val pw = new PrintWriter(svFile)
        newLines.foreach(pw.println)
        pw.close()
    }

    def addRwAddrCollisionAttr(svFile: String): Unit = {
        // 匹配形如  reg [xx:0] Memory[0:xxxx];
        val pattern = """(\s*)reg\s*\[[^]]+\]\s+Memory\s*\[[^]]+\];""".r
        val lines = Source.fromFile(svFile).getLines().toList
        // 对每一行检查：若匹配，则在前插入属性
        val newLines = lines.flatMap { line =>
            pattern.findFirstIn(line) match {
                case Some(_) =>
                    Seq("(* rw_addr_collision = \"yes\" *)", line)
                case None =>
                    Seq(line)
            }
        }
        val pw = new PrintWriter(svFile)
        newLines.foreach(pw.println)
        pw.close()
    }
}