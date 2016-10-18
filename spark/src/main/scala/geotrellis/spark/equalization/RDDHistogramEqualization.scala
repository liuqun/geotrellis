package geotrellis.spark.equalization

import geotrellis.raster._
import geotrellis.raster.equalization.HistogramEqualization
import geotrellis.raster.histogram.StreamingHistogram
import geotrellis.spark._

import org.apache.spark.rdd.RDD

import scala.reflect._


object RDDHistogramEqualization {

  private def r(left: Array[StreamingHistogram], right: Array[StreamingHistogram]) = {
    left.zip(right)
      .map({ case (l, r) => l + r })
  }

  /**
    * Given an RDD of [[Tile]] objects, return another RDD of tiles
    * where the respective tiles have had their histograms equalized
    * the joint histogram of all of the tiles.
    *
    * @param  rdd  An RDD of tile objects
    */
  def singleband[K, V: (? => Tile): ClassTag, M](
    rdd: RDD[(K, V)] with Metadata[M]
  ): RDD[(K, Tile)] with Metadata[M] = {
    val histogram = rdd
      .map({ case (_, tile: Tile) => StreamingHistogram.fromTile(tile, 1<<17)  })
      .reduce(_ + _)

    singleband(rdd, histogram)
  }

  /**
    * Given an RDD of [[Tile]] objects and a [[StreamingHistogram]]
    * derived from all of the tiles, return another RDD of tiles where
    * the respective tiles have had their histograms equalized.
    *
    * @param  rdd        An RDD of tile objects
    * @param  histogram  A histogram derived from the whole RDD of tiles
    */
  def singleband[K, V: (? => Tile): ClassTag, M](
    rdd: RDD[(K, V)] with Metadata[M],
    histogram: StreamingHistogram
  ): RDD[(K, Tile)] with Metadata[M] = {
    ContextRDD(
      rdd.map({ case (key, tile: Tile) =>
        (key, HistogramEqualization(tile, histogram)) }),
      rdd.metadata
    )
  }

  /**
    * Given an RDD of [[MultibandTile]] objects, return another RDD of
    * multiband tiles where the respective bands of the respective
    * tiles have been equalized according to a joint histogram of the
    * bands of the input RDD.
    *
    * @param  rdd  An RDD of multiband tile objects
    */
  def multiband[K, V: (? => MultibandTile): ClassTag, M](
    rdd: RDD[(K, V)] with Metadata[M]
  ): RDD[(K, MultibandTile)] with Metadata[M] = {
    val histograms = rdd
      .map({ case (_, tile: MultibandTile) =>
        tile.bands
          .map({ band => StreamingHistogram.fromTile(band, 1<<17) })
          .toArray })
      .reduce(r)

    multiband(rdd, histograms)
  }

  /**
    * Given an RDD of [[MultibandTile]] objects and a sequence of
    * [[StreamingHistogram]] objects (on per band) derived from all of
    * the tiles, return another RDD of multiband tiles where the
    * respective bands of the respective tiles have had their
    * histograms equalized.
    *
    * @param  rdd         An RDD of tile objects
    * @param  histograms  A histogram derived from the whole RDD of tiles
    */
  def multiband[K, V: (? => MultibandTile): ClassTag, M](
    rdd: RDD[(K, V)] with Metadata[M],
    histograms: Array[StreamingHistogram]
  ): RDD[(K, MultibandTile)] with Metadata[M] = {
    ContextRDD(
      rdd.map({ case (key, tile: MultibandTile) =>
        (key, HistogramEqualization(tile, histograms)) }),
      rdd.metadata
    )
  }

}
