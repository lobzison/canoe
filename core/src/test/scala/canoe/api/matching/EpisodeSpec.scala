package canoe.api.matching

import canoe.TestIO._
import cats.effect.IO
import fs2.Stream
import org.scalatest.funsuite.AnyFunSuite

class EpisodeSpec extends AnyFunSuite {

  val expected: String = "fire"

  val predicate: String => Boolean = _.endsWith(expected)

  test("Episode.start >>= Episode.next") {
    val episode: Episode[fs2.Pure, String, String] =
      for {
        m <- Episode.first[fs2.Pure, String](_.endsWith("one"))
        _ <- Episode.next[fs2.Pure, String](_.endsWith("two"))
      } yield m

    val input = Stream("one", "two")

    assert(input.through(episode.pipe).toList().size == 1)
  }

  test("Episode doesn't ignore the element which is mismatched") {
    val episode: Episode[fs2.Pure, String, String] =
      for {
        m <- Episode.first[fs2.Pure, String](_.endsWith("one"))
        _ <- Episode.next[fs2.Pure, String](_.endsWith("two"))
      } yield m

    val input = Stream("1.one", "2.one", "3.two")

    assert(input.through(episode.pipe).toList().head.take(1) == "2")
  }

  test("Episode can be cancelled while it's in progress") {
    val cancelToken = "cancel"

    val episode: Episode[fs2.Pure, String, String] =
      (for {
        m <- Episode.first[fs2.Pure, String](_.endsWith("one"))
        _ <- Episode.next[fs2.Pure, String](_ => true)
      } yield m).cancelOn(_ == cancelToken)

    val input = Stream("1.one", cancelToken, "any")

    assert(input.through(episode.pipe).size == 0)
  }

  test("Episode evaluates cancellation function when it is cancelled") {
    var cancelledWith = ""
    val cancelToken = "cancel"

    val episode: Episode[IO, String, String] =
      (for {
        m <- Episode.first[IO, String](_.endsWith("one"))
        _ <- Episode.next[IO, String](_ => true)
      } yield m).cancelWith[String](_ == cancelToken)(m => IO { cancelledWith = m })

    val input = Stream("1.one", cancelToken, "any")
    input.through(episode.pipe).run()

    assert(cancelledWith == cancelToken)
  }

  test("Episode.start needs at least one message") {
    val episode: Episode[fs2.Pure, String, String] = Episode.first(predicate)
    val input = Stream.empty

    assert(input.through(episode.pipe).toList().isEmpty)
  }

  test("Episode.start returns all matched occurrences") {
    val episode: Episode[fs2.Pure, String, String] = Episode.first(predicate)
    val input = Stream(
      s"1.$expected",
      s"1.$expected",
      s"1.",
      s"2.$expected"
    )

    assert(input.through(episode.pipe).size() == input.toList().count(predicate))
  }


  test("Episode.next needs at least one message") {
    val episode: Episode[fs2.Pure, String, String] = Episode.next(predicate)
    val input = Stream.empty

    assert(input.through(episode.pipe).toList().isEmpty)
  }

  test("Episode.next matches only the first message") {
    val episode: Episode[fs2.Pure, String, String] = Episode.next(predicate)

    val input = Stream(s"1.$expected", s"2.$expected")

    val results = input.through(episode.pipe).toList()
    assert(results.size == 1)
    assert(results.head.startsWith("1"))
  }

  test("Episode.next uses provided predicate to match the result") {
    val episode: Episode[fs2.Pure, String, String] = Episode.next(predicate)
    val input = Stream("")

    assert(input.through(episode.pipe).toList().isEmpty)
  }


  test("Episode.next#tolerate doesn't skip the element if it matches") {
    val episode: Episode[IO, String, String] =
      Episode.next(predicate).tolerateN(1)(_ => IO.unit)

    val input = Stream(s"1.$expected", s"2.$expected")

    assert(input.through(episode.pipe).toList().head.startsWith("1"))
  }

  test("Episode.next#tolerateN skips up to N elements if they don't match") {
    val n = 5
    val episode: Episode[IO, String, String] =
      Episode.next(predicate).tolerateN(n)(_ => IO.unit)

    val input = Stream("").repeatN(5) ++ Stream(s"2.$expected")

    assert(input.through(episode.pipe).toList().head.startsWith("2"))
  }

  test("Episode.eval doesn't consume any message") {
    val episode: Episode[IO, Unit, Unit] = Episode.eval(IO.unit)
    val input: Stream[fs2.Pure, Unit] = Stream.empty

    assert(input.through(episode.pipe).size == 1)
  }

  test("Episode.eval evaluates effect") {
    var evaluated = false
    val episode: Episode[IO, Unit, Unit] = Episode.eval(IO { evaluated = true })
    val input: Stream[fs2.Pure, Unit] = Stream.empty

    input.through(episode.pipe).run()

    assert(evaluated)
  }

  test("Episode.eval evaluates value in an effect") {
    val episode: Episode[IO, Unit, Int] = Episode.eval(IO.pure(1))
    val input: Stream[fs2.Pure, Unit] = Stream.empty

    assert(input.through(episode.pipe).value() == 1)
  }

  test("Episode.eval evaluates effect only once") {
    var times = 0
    val episode: Episode[IO, Unit, Unit] = Episode.eval(IO { times = times + 1 })
    val input: Stream[fs2.Pure, Unit] = Stream.empty

    input.through(episode.pipe).run()
    assert(times == 1)
  }
}