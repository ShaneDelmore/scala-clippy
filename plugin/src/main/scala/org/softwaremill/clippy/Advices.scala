package org.softwaremill.clippy

import java.io.{IOException, PrintWriter, StringWriter}
import java.net.{URI, URL}

import com.softwaremill.clippy.{Advice, NotFoundAdvice, TypeMismatchAdvice}

import scala.tools.nsc.Global
import scala.util.Try
import scala.xml.{XML, NodeSeq}

object Advices {

  def loadFromClasspath(global: Global): List[Advice] = {
    import scala.collection.JavaConversions._
    val i = enumerationAsScalaIterator(this.getClass.getClassLoader.getResources("clippy.xml"))
    i.toList.flatMap(loadFromURL(global))
  }

  def loadFromProjectClasspath(global: Global): List[Advice] = {
    val allUrls = global.classPath.asURLs
    allUrls.filter(_.getPath.endsWith(".jar")).map(addClippyXml).toList.flatMap(loadFromURL(global))
  }

  private def addClippyXml(url: URL): URL = URI.create("jar:file://" + url.getPath + "!/clippy.xml").toURL

  def loadFromURL(global: Global)(xml: URL): List[Advice] = loadFromXml(parseXml(global, xml).getOrElse(Nil))

  def parseXml(global: Global, xml: URL): Try[NodeSeq] = {
    val loadResult = Try(XML.load(xml))
    if (loadResult.isFailure) {
      loadResult.failed.foreach {
        case e: IOException => ()
        case otherException =>
          val sw: StringWriter = new StringWriter()
          otherException.printStackTrace(new PrintWriter(sw))
          global.warning(s"Error when parsing $xml: $sw")
      }
    }
    loadResult
  }

  def loadFromXml(xml: NodeSeq): List[Advice] = {
    (xml \\ "typemismatch").map { n =>
      TypeMismatchAdvice(
        (n \ "found").text,
        (n \ "required").text,
        (n \ "advice").text
      )
    }.toList ++ (xml \\ "notfound").map { n =>
      NotFoundAdvice(
        (n \ "what").text,
        (n \ "advice").text
      )
    }.toList
  }

}