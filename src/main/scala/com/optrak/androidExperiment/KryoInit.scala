package com.optrak.androidExperiment

import com.esotericsoftware.kryo.Kryo
import de.javakaffee.kryoserializers.jodatime.JodaDateTimeSerializer
import org.joda.time.DateTime

/**
 * Created by oscarvarto on 2014/08/01.
 */
class KryoInit {
  def customize(kryo: Kryo): Unit  = {
    kryo.register(classOf[DateTime], new JodaDateTimeSerializer)
    kryo.setReferences(false)
  }
}