package org.courio.opengraph

import io.youi.client.HttpClient
import io.youi.net._
import org.jsoup.Jsoup

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
                     `type`: Option[String],
                     favIcons: List[FavIcon])

object OpenGraph {
  private val client = HttpClient

  private val SizeRegex = "(\\d+)x(\\d+)".r

  def apply(url: URL): Future[OpenGraph] = {
    client.url(url).send().map { response =>
      response.content match {
        case Some(content) if content.contentType == ContentType.`text/html` => {
          val doc = Jsoup.parse(content.asString)
          val favIcons = doc
            .head()
            .select("link[rel=icon]")
            .asScala
            .toList
            .map { e =>
              val url = URL(e.attr("href"))
              val size = e.attr("sizes") match {
                case SizeRegex(w, h) => Some(w.toInt -> h.toInt)
                case _ => None
              }
              FavIcon(url, size)
            }
          scribe.info(s"FavIcon: $favIcons")
          val attributes = doc
            .head()
            .getElementsByTag("meta")
            .asScala
            .toList
            .filter(_.attr("property").startsWith("og:"))
            .map { e =>
              e.attr("property") -> e.attr("content")
            }
            .toMap
          OpenGraph(
            title = attributes.getOrElse("og:title", doc.title()),
            siteName = attributes.get("og:site_name"),
            site = attributes.get("og:site"),
            description = attributes.get("og:description"),
            image = attributes.get("og:image").orElse(attributes.get("og:image:url")).map(URL.apply),
            imageSecure = attributes.get("og:image:secure_url").map(URL.apply),
            imageType = attributes.get("og:image:type"),
            imageWidth = attributes.get("og:image:width").map(_.toInt),
            imageHeight = attributes.get("og:image:height").map(_.toInt),
            imageAlt = attributes.get("og:image:alt"),
            url = attributes.get("og:url").map(URL.apply),
            audio = attributes.get("og:audio").map(URL.apply),
            video = attributes.get("og:video").map(URL.apply),
            determiner = attributes.get("og:determiner"),
            locale = attributes.get("og:locale"),
            localeAlternate = attributes.get("og:locale:alternate"),
            `type` = attributes.get("og:type"),
            favIcons = favIcons
          )
        }
        case _ => throw new RuntimeException(s"$url is not an HTML page!")
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val future = apply(url"https://techcrunch.com/2019/10/13/ban-facebook-campaign-ads/?utm_medium=TCnewsletter&tpcc=TCdailynewsletter")
    val og = Await.result(future, Duration.Inf)
    scribe.info(s"OG: $og")
    System.exit(0)
  }
}

case class FavIcon(url: URL, size: Option[(Int, Int)])