package forex.domain

import cats.Show

sealed trait Currency

object Currency {
  case object AUD extends Currency
  case object CAD extends Currency
  case object CHF extends Currency
  case object EUR extends Currency
  case object GBP extends Currency
  case object NZD extends Currency
  case object JPY extends Currency
  case object SGD extends Currency
  case object USD extends Currency
  case object CNY extends Currency
  case object HKD extends Currency
  case object INR extends Currency
  case object KRW extends Currency
  case object THB extends Currency
  case object IDR extends Currency
  case object MYR extends Currency
  case object PHP extends Currency
  case object VND extends Currency
  case object TWD extends Currency
  case object PKR extends Currency
  case object BDT extends Currency
  case object LKR extends Currency
  case object NOK extends Currency
  case object SEK extends Currency
  case object DKK extends Currency
  case object PLN extends Currency
  case object CZK extends Currency
  case object HUF extends Currency
  case object RON extends Currency
  case object BGN extends Currency
  case object HRK extends Currency
  case object ISK extends Currency
  case object TRY extends Currency
  case object RUB extends Currency
  case object UAH extends Currency
  case object MXN extends Currency
  case object BRL extends Currency
  case object ARS extends Currency
  case object CLP extends Currency
  case object COP extends Currency
  case object PEN extends Currency
  case object VEF extends Currency
  case object UYU extends Currency
  case object AED extends Currency
  case object SAR extends Currency
  case object QAR extends Currency
  case object KWD extends Currency
  case object OMR extends Currency
  case object BHD extends Currency
  case object JOD extends Currency
  case object ILS extends Currency
  case object EGP extends Currency
  case object ZAR extends Currency
  case object NGN extends Currency
  case object KES extends Currency
  case object GHS extends Currency
  case object UGX extends Currency
  case object TZS extends Currency
  case object MAD extends Currency
  case object ETB extends Currency
  case object FJD extends Currency
  case object IQD extends Currency
  case object IRR extends Currency
  case object AFN extends Currency
  case object ALL extends Currency
  case object AMD extends Currency
  case object AOA extends Currency
  case object AZN extends Currency
  case object BAM extends Currency
  case object BBD extends Currency
  case object BND extends Currency
  case object BOB extends Currency
  case object BWP extends Currency
  case object BYN extends Currency
  case object BZD extends Currency
  case object CDF extends Currency
  case object CRC extends Currency
  case object CUP extends Currency
  case object DOP extends Currency
  case object DZD extends Currency
  case object GEL extends Currency
  case object GTQ extends Currency
  case object HNL extends Currency
  case object HTG extends Currency
  case object JMD extends Currency
  case object KGS extends Currency
  case object KHR extends Currency
  case object KZT extends Currency
  case object LAK extends Currency
  case object LBP extends Currency
  case object LYD extends Currency
  case object MMK extends Currency
  case object MNT extends Currency
  case object MOP extends Currency
  case object MUR extends Currency
  case object MVR extends Currency
  case object MWK extends Currency
  case object MZN extends Currency
  case object NAD extends Currency
  case object NIO extends Currency
  case object NPR extends Currency
  case object PAB extends Currency
  case object PGK extends Currency
  case object PYG extends Currency
  case object RSD extends Currency
  case object RWF extends Currency
  case object SYP extends Currency
  case object TJS extends Currency
  case object TMT extends Currency
  case object TND extends Currency
  case object TOP extends Currency
  case object TTD extends Currency
  case object UZS extends Currency
  case object WST extends Currency
  case object XAF extends Currency
  case object XCD extends Currency
  case object XOF extends Currency
  case object XPF extends Currency
  case object YER extends Currency
  case object ZMW extends Currency
  case object ZWL extends Currency

  val all: List[Currency] = List(
    AUD, CAD, CHF, EUR, GBP, NZD, JPY, SGD, USD,
    CNY, HKD, INR, KRW, THB, IDR, MYR, PHP, VND, TWD, PKR, BDT, LKR,
    NOK, SEK, DKK, PLN, CZK, HUF, RON, BGN, HRK, ISK, TRY, RUB, UAH,
    MXN, BRL, ARS, CLP, COP, PEN, VEF, UYU,
    AED, SAR, QAR, KWD, OMR, BHD, JOD, ILS, EGP,
    ZAR, NGN, KES, GHS, UGX, TZS, MAD, ETB,
    FJD, IQD, IRR, AFN, ALL, AMD, AOA, AZN, BAM, BBD, BND, BOB, BWP, 
    BYN, BZD, CDF, CRC, CUP, DOP, DZD, GEL, GTQ, HNL, HTG, JMD, KGS, 
    KHR, KZT, LAK, LBP, LYD, MMK, MNT, MOP, MUR, MVR, MWK, MZN, NAD, 
    NIO, NPR, PAB, PGK, PYG, RSD, RWF, SYP, TJS, TMT, TND, TOP, TTD, 
    UZS, WST, XAF, XCD, XOF, XPF, YER, ZMW, ZWL
  )

  implicit val show: Show[Currency] = Show.show(_.toString)

  def fromStringOpt(s: String): Option[Currency] = 
    try {
      Some(fromString(s))
    } catch {
      case _: MatchError => None
    }

  def fromString(s: String): Currency = s.toUpperCase match {
    case "AUD" => AUD
    case "CAD" => CAD
    case "CHF" => CHF
    case "EUR" => EUR
    case "GBP" => GBP
    case "NZD" => NZD
    case "JPY" => JPY
    case "SGD" => SGD
    case "USD" => USD
    case "CNY" => CNY
    case "HKD" => HKD
    case "INR" => INR
    case "KRW" => KRW
    case "THB" => THB
    case "IDR" => IDR
    case "MYR" => MYR
    case "PHP" => PHP
    case "VND" => VND
    case "TWD" => TWD
    case "PKR" => PKR
    case "BDT" => BDT
    case "LKR" => LKR
    case "NOK" => NOK
    case "SEK" => SEK
    case "DKK" => DKK
    case "PLN" => PLN
    case "CZK" => CZK
    case "HUF" => HUF
    case "RON" => RON
    case "BGN" => BGN
    case "HRK" => HRK
    case "ISK" => ISK
    case "TRY" => TRY
    case "RUB" => RUB
    case "UAH" => UAH
    case "MXN" => MXN
    case "BRL" => BRL
    case "ARS" => ARS
    case "CLP" => CLP
    case "COP" => COP
    case "PEN" => PEN
    case "VEF" => VEF
    case "UYU" => UYU
    case "AED" => AED
    case "SAR" => SAR
    case "QAR" => QAR
    case "KWD" => KWD
    case "OMR" => OMR
    case "BHD" => BHD
    case "JOD" => JOD
    case "ILS" => ILS
    case "EGP" => EGP
    case "ZAR" => ZAR
    case "NGN" => NGN
    case "KES" => KES
    case "GHS" => GHS
    case "UGX" => UGX
    case "TZS" => TZS
    case "MAD" => MAD
    case "ETB" => ETB
    case "FJD" => FJD
    case "IQD" => IQD
    case "IRR" => IRR
    case "AFN" => AFN
    case "ALL" => ALL
    case "AMD" => AMD
    case "AOA" => AOA
    case "AZN" => AZN
    case "BAM" => BAM
    case "BBD" => BBD
    case "BND" => BND
    case "BOB" => BOB
    case "BWP" => BWP
    case "BYN" => BYN
    case "BZD" => BZD
    case "CDF" => CDF
    case "CRC" => CRC
    case "CUP" => CUP
    case "DOP" => DOP
    case "DZD" => DZD
    case "GEL" => GEL
    case "GTQ" => GTQ
    case "HNL" => HNL
    case "HTG" => HTG
    case "JMD" => JMD
    case "KGS" => KGS
    case "KHR" => KHR
    case "KZT" => KZT
    case "LAK" => LAK
    case "LBP" => LBP
    case "LYD" => LYD
    case "MMK" => MMK
    case "MNT" => MNT
    case "MOP" => MOP
    case "MUR" => MUR
    case "MVR" => MVR
    case "MWK" => MWK
    case "MZN" => MZN
    case "NAD" => NAD
    case "NIO" => NIO
    case "NPR" => NPR
    case "PAB" => PAB
    case "PGK" => PGK
    case "PYG" => PYG
    case "RSD" => RSD
    case "RWF" => RWF
    case "SYP" => SYP
    case "TJS" => TJS
    case "TMT" => TMT
    case "TND" => TND
    case "TOP" => TOP
    case "TTD" => TTD
    case "UZS" => UZS
    case "WST" => WST
    case "XAF" => XAF
    case "XCD" => XCD
    case "XOF" => XOF
    case "XPF" => XPF
    case "YER" => YER
    case "ZMW" => ZMW
    case "ZWL" => ZWL
  }

}
