package forex.domain


case class OneFrame(
     from: String,
     to: String,
     bid: Double,
     ask: Double,
     price: Double,
     time_stamp: String
){
  @Override
  override def toString: String = {
    s"OneFrame(from=$from, to=$to, bid=$bid, ask=$ask, price=$price, time_stamp=$time_stamp)"
  }
}