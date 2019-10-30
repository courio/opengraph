package org.courio.opengraph

import java.io.File

import io.youi.client.HttpClient
import io.youi.http.content.{BytesContent, Content, FileContent}
import io.youi.net._
import io.youi.stream.IO
import org.jsoup.Jsoup
import org.matthicks.media4s.image.{ImageInfo, ImageUtil}

import scala.jdk.CollectionConverters._
import scala.concurrent.{Await, Future}
import scribe.Execution.global

import scala.concurrent.duration.Duration

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
                     preview: Option[File])

object OpenGraph {
  private val client = HttpClient

  private val SizeRegex = "(\\d+)x(\\d+)".r

  def apply(url: URL, config: OpenGraphConfig = OpenGraphConfig()): Future[Option[OpenGraph]] = {
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
                val iconURL = URL.get(href) match {
                  case Left(_) => url.withPath(href)
                  case Right(u) => u
                }
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
            val imageURL = properties.get("og:image").orElse(properties.get("og:image:url")).map(URL.apply)
            scribe.info(s"Image URL: $imageURL")
            val previewFuture: Future[Option[(File, ImageInfo)]] = imageURL match {
              case Some(u) => HttpClient.url(u).send().map { response =>
                val content = response.content.getOrElse(throw new RuntimeException(s"No content returned for $u"))
                Some(createPreview(content, config))
              }
              case None => Future.successful(None)
            }
            previewFuture.map(_.map(_._1)).map { preview =>
              Some(OpenGraph(
                title = properties.getOrElse("og:title", parseTitle(doc.title())),
                siteName = properties.get("og:site_name"),
                site = properties.get("og:site").orElse(parseSite(doc.title())),
                description = properties.get("og:description"),
                image = imageURL,
                imageSecure = properties.get("og:image:secure_url").map(URL.apply),
                imageType = properties.get("og:image:type"),
                imageWidth = properties.get("og:image:width").map(_.toInt),
                imageHeight = properties.get("og:image:height").map(_.toInt),
                imageAlt = properties.get("og:image:alt"),
                url = properties.get("og:url").map(URL.apply),
                audio = properties.get("og:audio").map(URL.apply),
                video = properties.get("og:video").map(URL.apply),
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
          case ct if ct.`type` == "image" => {
            val title = url.path.parts.last.value
            val (preview, imageInfo) = createPreview(content, config)
            Future.successful(Some(OpenGraph(
              title = title,
              siteName = None,
              site = None,
              description = None,
              image = Some(url),
              imageSecure = None,
              imageType = imageInfo.imageType.map(_.mimeType),
              imageWidth = Some(imageInfo.width),
              imageHeight = Some(imageInfo.height),
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
            Future.successful(None)
          }
        }
        case _ => {
          scribe.warn(s"No content returned for $url")
          Future.successful(None)
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

  def createPreview(content: Content, config: OpenGraphConfig): (File, ImageInfo) = {
    val file = content match {
      case FileContent(f, _, _) => f
      case BytesContent(value, contentType, _) => {
        val temp = File.createTempFile("opengraph", s".${contentType.extension.getOrElse(throw new RuntimeException(s"No extension defined for $contentType"))}", config.directory)
        IO.stream(value, temp)
        temp
      }
    }
    val imageInfo = ImageUtil.info(file)
    (createPreview(file, imageInfo, config), imageInfo)
  }

  def createPreview(file: File, imageInfo: ImageInfo, config: OpenGraphConfig): File = {
    val extension = imageInfo.imageType.map(_.extension).getOrElse(throw new RuntimeException(s"No image-type for ${file.getAbsolutePath} - $imageInfo"))
    val temp = File.createTempFile("preview", s".$extension", config.directory)
    ImageUtil.generateResized(file, temp, width = Some(config.previewMaxWidth), height = Some(config.previewMaxHeight))
    temp
  }

  def main(args: Array[String]): Unit = {
    val future = apply(url"https://techcrunch.com/2019/10/13/ban-facebook-campaign-ads/?utm_medium=TCnewsletter&tpcc=TCdailynewsletter")
//    val future = apply(url"https://www.nytimes.com/2016/08/28/opinion/sunday/even-roger-federer-gets-old.html?ref=oembed")
//    val future = apply(url"https://www.outr.com")
//    val future = apply(url"https://courio.com/images/desktop.png")
    val og = Await.result(future, Duration.Inf)
    scribe.info(s"OG: $og")
    scribe.info(s"Preview: ${og.foreach(_.preview.foreach(_.getAbsolutePath))}")
    System.exit(0)
  }
}