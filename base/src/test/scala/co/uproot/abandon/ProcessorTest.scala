package co.uproot.abandon

import java.io.ByteArrayOutputStream

import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.scalatest.Inside

import TestHelper._
import ParserHelper._

class ProcessorTest extends FlatSpec with Matchers with Inside {
  val parser = new AbandonParser(None)

  "Processor" should "export a simple transaction in ledger Format" in {
    val testInput = """
    2013/1/1
      Expense       -(200 + 40)
      Cash
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val appState = Processor.process(scope, Nil, None)
        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), false, Nil)

        val settings = Settings(Nil, Nil, Nil, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val ledgerRep = Reports.ledgerExport(appState, settings, balSettings)
        inside(ledgerRep) {
          case Seq(LedgerExportData(date, ledgerEntries)) =>
            date should be(Date(2013, 1, 1))
            inside(ledgerEntries) {
              case Seq(LedgerExportEntry(acc1, expr1, None), LedgerExportEntry(acc2, expr2, None)) =>
                acc1 should be (cashAccount)
                acc2 should be (expenseAccount)
                expr1 should be (240)
                expr2 should be (-240)
            }
        }
    }
  }

  it should "not export a transaction with zero value when showZeroAmount is False" in {
    val testInput = """
    2013/1/1
      Expense       -(200 + 40)
      Cash
      Bank           0000
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val appState = Processor.process(scope, Nil, None)

        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), false, Nil)
        val settings = Settings(Nil, Nil, Nil, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val ledgerRep = Reports.ledgerExport(appState, settings, balSettings)
        inside(ledgerRep) {
          case Seq(LedgerExportData(date, ledgerEntries)) =>
            date should be(Date(2013, 1, 1))
            inside(ledgerEntries) {
              case Seq(LedgerExportEntry(acc1, expr1, None), LedgerExportEntry(acc2, expr2, None)) =>
                acc1 should be (cashAccount)
                acc2 should be (expenseAccount)
                expr1 should be (240)
                expr2 should be (-240)
            }
        }
    }
  }

  it should "export a transaction with zero value when showZeroAmount is True" in {
    val testInput = """
    2013/1/1
      Expense       -200
      Cash
      Bank:Current           0000
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val appState = Processor.process(scope, Nil, None)

        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), true, Nil)
        val settings = Settings(Nil, Nil, Nil, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val ledgerRep = Reports.ledgerExport(appState, settings, balSettings)
        inside(ledgerRep) {
          case Seq(LedgerExportData(date, ledgerEntries)) =>
            date should be(Date(2013, 1, 1))
            inside(ledgerEntries) {
              case Seq(LedgerExportEntry(acc1, expr1, None), LedgerExportEntry(acc2, expr2, None), LedgerExportEntry(acc3, expr3, None)) =>
                acc1 should be (bankAccount)
                acc2 should be (cashAccount)
                acc3 should be (expenseAccount)
                expr1 should be (0)
                expr2 should be (200)
                expr3 should be (-200)
            }
        }
    }
  }

  it should "export no transaction for empty input" in {
    val testInput = """
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val appState = Processor.process(scope, Nil, None)

        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), false, Nil)
        val settings = Settings(Nil, Nil, Nil, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val ledgerRep = Reports.ledgerExport(appState, settings, balSettings)
        ledgerRep should be (Nil)
    }
  }

  it should "export transaction with closing balance" in {
    val testInput = """
      2013/1/1
      Expense       4000
      Income           -1000
      Equity    10000
      Assets -13000
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val appState = Processor.process(scope, Nil, None)
        val source = Seq("Income", "Expense")
        val destination = "Equity"
        val closure = Seq(ClosureExportSettings(source, destination))

        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), false, closure)
        val settings = Settings(Nil, Nil, Nil, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val ledgerRep = Reports.ledgerExport(appState, settings, balSettings)
        inside(ledgerRep) {
          case Seq(LedgerExportData(date, ledgerEntries), LedgerExportData(date1, ledgerEntries1)) =>
            date should be(Date(2013, 1, 1))
            inside(ledgerEntries) {
              case Seq(LedgerExportEntry(acc1, expr1, None), LedgerExportEntry(acc2, expr2, None), LedgerExportEntry(acc3, expr3, None), LedgerExportEntry(acc4, expr4, None)) =>
                acc1 should be (assetsAccount)
                acc2 should be (equityAccount)
                acc3 should be (expenseAccount)
                acc4 should be (incomeAccount)
                expr1 should be (-13000)
                expr2 should be (10000)
                expr3 should be (4000)
                expr4 should be (-1000)
            }
            date1 should be(Date(2013, 1, 1))
            inside(ledgerEntries1) {
              case Seq(LedgerExportEntry(acc1, expr1, None), LedgerExportEntry(acc2, expr2, None), LedgerExportEntry(acc3, expr3, Some(" result: 13000"))) =>
                acc1 should be (expenseAccount)
                acc2 should be (incomeAccount)
                acc3 should be (equityAccount)
                expr1 should be (-4000)
                expr2 should be (1000)
                expr3 should be (3000)
            }
        }

    }

  }

  it should "show Exception when 'source' contains same 'accountName'" in {
    val testInput = """
      2013/1/1
      Expense       4000
      Income           -1000
      Equity    10000
      Assets -13000
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val appState = Processor.process(scope, Nil, None)
        val source = Seq("Income", "Expense")
        val destination = "Equity"
        val source1 = Seq("Income", "Expense")
        val destination1 = "Equity"
        val closure1 = Seq(ClosureExportSettings(source, destination))
        val closure2 = Seq(ClosureExportSettings(source1, destination1))
        val closure = closure1 ++ closure2
        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), false, closure)

        val settings = Settings(Nil, Nil, Nil, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val ledgerRep = intercept[InputError] {
          Reports.ledgerExport(appState, settings, balSettings)
        }
    }

  }

  it should "show Exception when 'destination' is not Present" in {
    val testInput = """
      2013/1/1
      Expense       14000
      Income        -1000
      Assets       -13000
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val appState = Processor.process(scope, Nil, None)
        val source = Seq("Income", "Expense")
        val destination = "NewAccount"
        val closure = Seq(ClosureExportSettings(source, destination))

        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), false, closure)
        val settings = Settings(Nil, Nil, Nil, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val ledgerRep = Reports.ledgerExport(appState, settings, balSettings)

        inside(ledgerRep) {
          case Seq(LedgerExportData(date, ledgerEntries), LedgerExportData(date1, ledgerEntries1)) =>
            date should be(Date(2013, 1, 1))
            inside(ledgerEntries) {
              case Seq(LedgerExportEntry(acc1, expr1, None), LedgerExportEntry(acc2, expr2, None), LedgerExportEntry(acc3, expr3, None)) =>
                acc1 should be (assetsAccount)
                acc2 should be (expenseAccount)
                acc3 should be (incomeAccount)
                expr1 should be (-13000)
                expr2 should be (14000)
                expr3 should be (-1000)
            }
            date1 should be(Date(2013, 1, 1))
            inside(ledgerEntries1) {
              case Seq(LedgerExportEntry(acc1, expr1, None), LedgerExportEntry(acc2, expr2, None), LedgerExportEntry(acc3, expr3, Some(" result: 13000"))) =>
                acc1 should be (expenseAccount)
                acc2 should be (incomeAccount)
                acc3 should be (AccountName(Seq("NewAccount")))
                expr1 should be (-14000)
                expr2 should be (1000)
                expr3 should be (13000)
            }
        }
    }
  }

  it should "show Exception when 'destination' is present in 'source'" in {
    val testInput = """
      2013/1/1
      Expense       14000
      Income        -1000
      Equity       -13000
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val appState = Processor.process(scope, Nil, None)
        val source = Seq("Income", "Equity")
        val destination = "Equity"
        val closure = Seq(ClosureExportSettings(source, destination))

        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), false, closure)
        val settings = Settings(Nil, Nil, Nil, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val ledgerRep = intercept[SourceDestinationClashError] {
          Reports.ledgerExport(appState, settings, balSettings)
        }

    }

  }

  it should "process Alias name" in {
    val testInput = """
      2013/1/1
      MyBank       14000
      Income        -1000
      Equity       -13000
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope, _) =>
        val name = AccountName(Seq("Bank","Current"))
        val alias = "MyBank"
        val accounts = Seq(AccountSettings(name, Some(alias)))

        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), false, Nil)
        val settings = Settings(Nil, Nil, accounts, Nil, ReportOptions(Nil), Seq(balSettings), None, false, None, None)

        val appState = Processor.process(scope, settings.accounts, None)

        val ledgerRep = Reports.ledgerExport(appState, settings, balSettings)
        inside(ledgerRep) {
          case Seq(LedgerExportData(date, ledgerEntries)) =>
            date should be(Date(2013, 1, 1))
            inside(ledgerEntries) {
              case Seq(LedgerExportEntry(acc1, expr1, None), LedgerExportEntry(acc2, expr2, None), LedgerExportEntry(acc3, expr3, None)) =>
                acc1 should be (bankAccount)
                acc2 should be (equityAccount)
                acc3 should be (incomeAccount)
                expr1 should be (14000)
                expr2 should be (-13000)
                expr3 should be (-1000)
            }
        }

    }

  }

  it should "scope after process should contain unused definitions" in {
    val testInput = """
      def used1   = 1
      def unused1 = 1

      {
      def unused2 = 2
      }

      2013/1/1
      MyBank       14000 * used1
      Income        -1000
      Equity       -13000
    """
    val parseResult = parser.abandon(scanner(testInput))
    inside(parseResult) {
      case parser.Success(scope: Scope, _) =>
        val name = AccountName(Seq("Bank","Current"))
        val alias = "MyBank"
        val accounts = Seq(AccountSettings(name, Some(alias)))

        val balSettings = LedgerExportSettings(None, Seq("balSheet12.txt"), showZeroAmountAccounts = false, Nil)
        val settings = Settings(Nil, Nil, accounts, Nil, ReportOptions(Nil), Seq(balSettings), None, quiet = false, None, None)

        val myOut = new ByteArrayOutputStream()
        Console.withOut(myOut) {
          Processor.process(scope, settings.accounts, None)
        }

        myOut.toString should (include("symbol 'unused1' defined ") and include("symbol 'unused2' defined ") and include(" but never used"))
    }
  }
}
