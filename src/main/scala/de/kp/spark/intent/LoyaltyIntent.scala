package de.kp.spark.intent
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
 * 
 * This file is part of the Spark-Intent project
 * (https://github.com/skrusche63/spark-intent).
 * 
 * Spark-Intent is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * Spark-Intent is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * Spark-Intent. 
 * 
 * If not, see <http://www.gnu.org/licenses/>.
 */

import de.kp.spark.intent.model._
import de.kp.spark.intent.redis.RedisCache

import de.kp.spark.intent.markov.HiddenMarkovModel
import de.kp.spark.intent.state.LoyaltyState

import scala.collection.mutable.ArrayBuffer

class LoyaltyIntent extends LoyaltyState {
  
  def predict(uid:String,data:Map[String,String]):String = {
    
    val model = new HiddenMarkovModel()

    val path = RedisCache.model(uid)    
    model.load(path)
    
    data.get("purchases") match {
      
      case None => throw new Exception(Messages.MISSING_PURCHASES(uid))
      
      case Some(value) => {
        
        val purchases = Serializer.deserializePurchases(value).items
        /*
         * Group purchases by site & user and restrict to those
         * users with more than one purchase
         */
        val behaviors = purchases.groupBy(p => (p.site,p.user)).filter(_._2.size > 1).map(p => {

          val (site,user) = p._1
          val orders      = p._2.map(v => (v.timestamp,v.amount)).toList.sortBy(_._1)
      
          /* Extract first order */
          var (pre_time,pre_amount) = orders.head
          
          val states = ArrayBuffer.empty[String]
          for ((time,amount) <- orders.tail) {
        
            /* Determine state from amount */
            val astate = stateByAmount(amount,pre_amount)
     
            /* Determine state from time elapsed between
             * subsequent orders or transactions
             */
            val tstate = stateByTime(time,pre_time)
      
            val state = astate + tstate
            states += state
        
            pre_amount = amount
            pre_time   = time
        
          }
      
          val observations = states.toArray
          val intents = model.predict(observations)
          
          new Behavior(site,user,intents.toList)
          
        }).toList
              
        Serializer.serializeBehaviors(new Behaviors(behaviors))

      }
    
    }
  
  }

}