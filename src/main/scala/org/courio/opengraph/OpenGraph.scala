package org.courio.opengraph

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import java.io.File
import org.jsoup.Jsoup
import org.matthicks.media4s.image.{ImageType, ImageUtil}
import org.matthicks.media4s.video.VideoUtil
import org.matthicks.media4s.video.transcode.FFMPEGTranscoder

import scala.jdk.CollectionConverters._
import spice.http.HttpStatus
import spice.http.client.HttpClient
import spice.http.content.{BytesContent, Content, FileContent}
import spice.net._
import spice.streamer.Streamer

import scala.concurrent.duration.Duration
import scala.util.Try

case class OpenGraph(title: String,
                     siteName: Option[String],
                     site: Option[String],
                     description: Option[String],
                     image: Option[URL],
                     imageSecure: Option[URL],
                     imageType: Option[String],
                     imageWidth: Option[Int],
                     imageHeight: Option[Int],
                     imageAlt: Option[String],
                     url: Option[URL],
                     audio: Option[URL],
                     video: Option[URL],
                     determiner: Option[String],
                     locale: Option[String],
                     localeAlternate: Option[String],
                     byline: Option[String],
                     `type`: Option[String],
                     favIcons: List[FavIcon],
                     preview: Option[OpenGraphPreview])

object OpenGraph {
  private val client = HttpClient

  private val SizeRegex = "(\\d+)x(\\d+)".r

  def apply(url: URL, config: OpenGraphConfig = OpenGraphConfig()): IO[Option[OpenGraph]] = {
    client.url(url).send().flatMap { response =>
      response.content match {
        case Some(content) => content.contentType match {
          case ContentType.`text/html` => {
            val doc = Jsoup.parse(content.asString)
            val favIcons = doc
              .head()
              .select("link[rel=icon]")
              .asScala
              .toList
              .map { e =>
                val href = e.attr("href")
                val iconURL = url.withPart(href)
                val size = e.attr("sizes") match {
                  case SizeRegex(w, h) => Some(w.toInt -> h.toInt)
                  case _ => None
                }
                FavIcon(iconURL, size)
              }
            val meta = doc.head().getElementsByTag("meta").asScala.toList
            val names = meta
              .map { e =>
                e.attr("name") -> e.attr("content")
              }
              .toMap
            val properties = meta
              .filter(_.attr("property").startsWith("og:"))
              .map { e =>
                e.attr("property") -> e.attr("content")
              }
              .toMap
            val imageURL = properties.get("og:image").orElse(properties.get("og:image:url")).map(_.trim).filterNot(_.isEmpty).map(URL.parse(_))
            val previewIO: IO[Option[OpenGraphPreview]] = imageURL match {
              case Some(u) => HttpClient.url(u).send().map { response =>
                if (response.status == HttpStatus.OK) {
                  val content = response.content.getOrElse(throw new RuntimeException(s"No content returned for $u"))
                  Some(createPreview(u.path.parts.last.value, content, config))
                } else {
                  scribe.warn(s"Bad status (${response.status}) received when trying to retrieve $u for $url")
                  None
                }
              }
              case None => IO.pure(None)
            }
            previewIO.map { preview =>
              Some(OpenGraph(
                title = properties.getOrElse("og:title", parseTitle(doc.title())),
                siteName = properties.get("og:site_name"),
                site = properties.get("og:site").orElse(parseSite(doc.title())),
                description = properties.get("og:description"),
                image = imageURL,
                imageSecure = properties.get("og:image:secure_url").map(URL.parse(_)),
                imageType = properties.get("og:image:type"),
                imageWidth = Try(properties.get("og:image:width").map(_.toInt)).getOrElse(None),
                imageHeight = Try(properties.get("og:image:height").map(_.toInt)).getOrElse(None),
                imageAlt = properties.get("og:image:alt"),
                url = properties.get("og:url").map(URL.get(_)).flatMap(_.toOption),
                audio = properties.get("og:audio").map(URL.get(_)).flatMap(_.toOption),
                video = properties.get("og:video").map(URL.get(_)).flatMap(_.toOption),
                determiner = properties.get("og:determiner"),
                locale = properties.get("og:locale"),
                localeAlternate = properties.get("og:locale:alternate"),
                byline = names.get("byl"),
                `type` = properties.get("og:type"),
                favIcons = favIcons,
                preview = preview
              ))
            }
          }
          case ct if ct.`type` == "image" || ct.`type` == "video" => {
            val title = url.path.parts.last.value
            val preview = createPreview(url.path.parts.last.value, content, config)
            IO.pure(Some(OpenGraph(
              title = title,
              siteName = None,
              site = None,
              description = None,
              image = Some(url),
              imageSecure = None,
              imageType = preview.info.imageType.map(_.mimeType),
              imageWidth = Some(preview.info.width),
              imageHeight = Some(preview.info.height),
              imageAlt = None,
              url = None,
              audio = None,
              video = None,
              determiner = None,
              locale = None,
              localeAlternate = None,
              byline = None,
              `type` = None,
              favIcons = Nil,
              preview = Some(preview)
            )))
          }
          case _ => {
            scribe.warn(s"Unsupported content-type: ${content.contentType} for $url")
            IO.pure(None)
          }
        }
        case _ => {
          scribe.warn(s"No content returned for $url")
          IO.pure(None)
        }
      }
    }
  }

  def parseTitle(title: String): String = {
    val dash = title.indexOf('-')
    if (dash != -1) {
      title.substring(0, dash).trim
    } else {
      title
    }
  }

  def parseSite(title: String): Option[String] = {
    val lastDash = title.lastIndexOf('-')
    if (lastDash != -1) {
      Some(title.substring(lastDash + 1).trim)
    } else {
      None
    }
  }

  def createPreview(fileName: String, content: Content, config: OpenGraphConfig): OpenGraphPreview = {
    val file = content match {
      case FileContent(f, _, _) => f
      case BytesContent(value, contentType, _) => {
        val extension = contentType.extension match {
          case Some(ext) => ext
          case None => ContentType.byFileName(fileName).extension.getOrElse(s"No extension defined for $contentType or file name: $fileName.")
        }
        val temp = File.createTempFile("opengraph", s".$extension", config.directory)
        Streamer(value, temp)
        temp
      }
    }
    val preview = createPreview(file, config)
    OpenGraphPreview(preview, ImageUtil.info(preview))
  }

  def createPreview(file: File, config: OpenGraphConfig): File = {
    val contentType = ContentType.byFileName(file.getName)
    scribe.info(s"Create Preview: ${file.getName}, Content-Type: $contentType")
    if (contentType.`type` == "video") {
      val temp = File.createTempFile("preview", ".png", config.directory)
      val videoInfo = VideoUtil.info(file)
      FFMPEGTranscoder()
        .input(file)
        .screenGrab(videoInfo.duration / 2.0)
        .output(temp)
        .execute(None)
      createPreview(temp, config)
    } else {
      val imageInfo = ImageUtil.info(file)
      val `type` = imageInfo.imageType.getOrElse(throw new RuntimeException(s"No image-type for ${file.getAbsolutePath} - $imageInfo"))
      val extension = `type`.extension
      val temp = File.createTempFile("preview", s".$extension", config.directory)
      val s = SizeUtility.scale(imageInfo.width, imageInfo.height, config.previewMaxWidth, config.previewMaxHeight, scaleUp = false)
      if (`type` == ImageType.GIF) {
        ImageUtil.generateGIFCropped(file, temp, s.width.toInt, s.height.toInt)
      } else {
        ImageUtil.generateResized(file, temp, width = Some(s.width.toInt), height = Some(s.height.toInt))
      }
      temp
    }
  }

  def main(args: Array[String]): Unit = {
    val config = OpenGraphConfig(previewMaxWidth = 600, previewMaxHeight = 400)
//    val io = apply(url"https://techcrunch.com/2019/10/13/ban-facebook-campaign-ads/?utm_medium=TCnewsletter&tpcc=TCdailynewsletter", config)
//    val io = apply(url"https://www.nytimes.com/2016/08/28/opinion/sunday/even-roger-federer-gets-old.html?ref=oembed")
//    val io = apply(url"https://www.outr.com")
//    val io = apply(url"https://courio.com/images/desktop.png")
//    val io = apply(url"https://jobs.lever.co/ycombinator/ef091f3d-df02-433c-a6c0-7ba4a0c70fa7")
    val io = apply(url"http://darkfrog.courio.com/?stream=%2BdeBk29bfsrTfvG8AVr3YrTpAVon1EL65&public=true")
    val ogOption = io.unsafeRunSync()
    ogOption.foreach { og =>
      scribe.info(s"FavIcon: ${og.favIcons}")
    }
    System.exit(0)
  }
}