package com.mixpanel.sessionreplaydemo.ui.jokes

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import com.mixpanel.sessionreplaydemo.data.Comment
import com.mixpanel.sessionreplaydemo.data.Joke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JokesViewModel : ViewModel() {

    private val _jokes = MutableStateFlow(generateSampleJokes())
    val jokes: StateFlow<List<Joke>> = _jokes.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    fun updateProgress(newProgress: Int) {
        _progress.value = newProgress
    }

    private fun generateSampleJokes(): List<Joke> = listOf(
        Joke(
            id = "1",
            setup = "Why don't scientists trust atoms?",
            punchline = "Because they make up everything!",
            category = "Science",
            thumbnailColor = Color(0xFF6200EE),
            likeCount = 1542,
            comments = listOf(
                Comment("c1", "Alice", "This is gold! 😂", "2h ago"),
                Comment("c2", "Bob", "I've heard this one before but still funny", "3h ago"),
                Comment("c3", "Charlie", "Classic!", "5h ago")
            )
        ),
        Joke(
            id = "2",
            setup = "What do you call a fake noodle?",
            punchline = "An impasta!",
            category = "Food",
            thumbnailColor = Color(0xFFFF6F00),
            likeCount = 2341,
            comments = listOf(
                Comment("c4", "Diana", "Love it! 🍝", "1h ago"),
                Comment("c5", "Ethan", "My kids would love this one", "2h ago"),
                Comment("c6", "Fiona", "Hahaha brilliant", "4h ago"),
                Comment("c7", "George", "I'm stealing this", "6h ago")
            )
        ),
        Joke(
            id = "3",
            setup = "Why did the scarecrow win an award?",
            punchline = "He was outstanding in his field!",
            category = "Farm",
            thumbnailColor = Color(0xFF388E3C),
            likeCount = 987,
            comments = listOf(
                Comment("c8", "Hannah", "Corny but funny", "30m ago"),
                Comment("c9", "Ian", "Award-winning joke indeed", "1h ago"),
                Comment("c10", "Julia", "Made me chuckle", "2h ago")
            )
        ),
        Joke(
            id = "4",
            setup = "What's the best thing about Switzerland?",
            punchline = "I don't know, but the flag is a big plus!",
            category = "Geography",
            thumbnailColor = Color(0xFFD32F2F),
            likeCount = 3210,
            comments = listOf(
                Comment("c11", "Kevin", "Switzerland approved! 🇨🇭", "1h ago"),
                Comment("c12", "Linda", "Very punny", "2h ago"),
                Comment("c13", "Mike", "This one's a keeper", "3h ago"),
                Comment("c14", "Nina", "😄", "4h ago")
            )
        ),
        Joke(
            id = "5",
            setup = "Why don't eggs tell jokes?",
            punchline = "They'd crack each other up!",
            category = "Food",
            thumbnailColor = Color(0xFFFBC02D),
            likeCount = 1876,
            comments = listOf(
                Comment("c15", "Oscar", "Egg-cellent! 🥚", "45m ago"),
                Comment("c16", "Penny", "This cracked me up!", "2h ago"),
                Comment("c17", "Quinn", "Simple but effective", "3h ago")
            )
        ),
        Joke(
            id = "6",
            setup = "How does a penguin build its house?",
            punchline = "Igloos it together!",
            category = "Animals",
            thumbnailColor = Color(0xFF0277BD),
            likeCount = 2567,
            comments = listOf(
                Comment("c18", "Rachel", "Adorable! 🐧", "1h ago"),
                Comment("c19", "Sam", "My favorite so far", "2h ago"),
                Comment("c20", "Tina", "Clever wordplay", "3h ago"),
                Comment("c21", "Uma", "LOL", "5h ago")
            )
        ),
        Joke(
            id = "7",
            setup = "What do you call a bear with no teeth?",
            punchline = "A gummy bear!",
            category = "Animals",
            thumbnailColor = Color(0xFF5D4037),
            likeCount = 4123,
            comments = listOf(
                Comment("c22", "Victor", "This is unbearably funny", "30m ago"),
                Comment("c23", "Wendy", "Kids love this one!", "1h ago"),
                Comment("c24", "Xavier", "😂😂😂", "2h ago")
            )
        ),
        Joke(
            id = "8",
            setup = "Why did the bicycle fall over?",
            punchline = "Because it was two-tired!",
            category = "Sports",
            thumbnailColor = Color(0xFF1976D2),
            likeCount = 1654,
            comments = listOf(
                Comment("c25", "Yara", "Wheely good joke", "1h ago"),
                Comment("c26", "Zack", "Can't stop laughing", "2h ago"),
                Comment("c27", "Amy", "Brilliant!", "3h ago"),
                Comment("c28", "Ben", "Sharing with my cycling club", "4h ago")
            )
        ),
        Joke(
            id = "9",
            setup = "What did the ocean say to the beach?",
            punchline = "Nothing, it just waved!",
            category = "Nature",
            thumbnailColor = Color(0xFF00ACC1),
            likeCount = 2890,
            comments = listOf(
                Comment("c29", "Cara", "Making waves with this joke 🌊", "2h ago"),
                Comment("c30", "Dan", "Shore is funny", "3h ago"),
                Comment("c31", "Eva", "Water great joke!", "4h ago")
            )
        ),
        Joke(
            id = "10",
            setup = "Why don't skeletons fight each other?",
            punchline = "They don't have the guts!",
            category = "Spooky",
            thumbnailColor = Color(0xFF7B1FA2),
            likeCount = 3456,
            comments = listOf(
                Comment("c32", "Frank", "Bone-chilling humor 💀", "1h ago"),
                Comment("c33", "Grace", "Spooky and funny", "2h ago"),
                Comment("c34", "Henry", "Perfect for Halloween", "3h ago"),
                Comment("c35", "Iris", "Can't stop laughing", "5h ago")
            )
        ),
        Joke(
            id = "11",
            setup = "What do you call cheese that isn't yours?",
            punchline = "Nacho cheese!",
            category = "Food",
            thumbnailColor = Color(0xFFF57C00),
            likeCount = 5234,
            comments = listOf(
                Comment("c36", "Jack", "Cheesy but good 🧀", "1h ago"),
                Comment("c37", "Kate", "Grate joke!", "2h ago"),
                Comment("c38", "Leo", "My kids say this all the time", "3h ago")
            )
        ),
        Joke(
            id = "12",
            setup = "Why did the math book look so sad?",
            punchline = "Because it had too many problems!",
            category = "School",
            thumbnailColor = Color(0xFF303F9F),
            likeCount = 2109,
            comments = listOf(
                Comment("c39", "Mia", "As a teacher, I can relate 📚", "2h ago"),
                Comment("c40", "Noah", "This adds up!", "3h ago"),
                Comment("c41", "Olivia", "Mathematically funny", "4h ago"),
                Comment("c42", "Paul", "Problem solved with humor", "5h ago")
            )
        ),
        Joke(
            id = "13",
            setup = "What do you call a sleeping bull?",
            punchline = "A bulldozer!",
            category = "Animals",
            thumbnailColor = Color(0xFF8D6E63),
            likeCount = 1789,
            comments = listOf(
                Comment("c43", "Quinn", "This one's a snoozer... in a good way!", "1h ago"),
                Comment("c44", "Ruby", "Moo-velous!", "2h ago"),
                Comment("c45", "Steve", "Bull's eye with this joke", "3h ago")
            )
        ),
        Joke(
            id = "14",
            setup = "Why can't you hear a pterodactyl using the bathroom?",
            punchline = "Because the 'P' is silent!",
            category = "Dinosaurs",
            thumbnailColor = Color(0xFF689F38),
            likeCount = 3987,
            comments = listOf(
                Comment("c46", "Tara", "Roar-some joke! 🦕", "1h ago"),
                Comment("c47", "Umar", "My dino-loving kid will love this", "2h ago"),
                Comment("c48", "Vera", "Prehistoric humor at its best", "3h ago"),
                Comment("c49", "Will", "Extinct-ly funny", "4h ago")
            )
        ),
        Joke(
            id = "15",
            setup = "What did one wall say to the other wall?",
            punchline = "I'll meet you at the corner!",
            category = "Random",
            thumbnailColor = Color(0xFF455A64),
            likeCount = 1432,
            comments = listOf(
                Comment("c50", "Xena", "Cornering the market on jokes", "2h ago"),
                Comment("c51", "Yuki", "Well-constructed humor", "3h ago"),
                Comment("c52", "Zoe", "This one's solid!", "4h ago")
            )
        ),
        Joke(
            id = "16",
            setup = "Why did the golfer bring two pairs of pants?",
            punchline = "In case he got a hole in one!",
            category = "Sports",
            thumbnailColor = Color(0xFF43A047),
            likeCount = 2654,
            comments = listOf(
                Comment("c53", "Adam", "Par for the course! ⛳", "1h ago"),
                Comment("c54", "Beth", "Tee-rific!", "2h ago"),
                Comment("c55", "Carl", "On par with the best jokes", "3h ago"),
                Comment("c56", "Deb", "Fairway to heaven", "4h ago")
            )
        ),
        Joke(
            id = "17",
            setup = "What do you call a fish wearing a bowtie?",
            punchline = "Sofishticated!",
            category = "Animals",
            thumbnailColor = Color(0xFF26A69A),
            likeCount = 3211,
            comments = listOf(
                Comment("c57", "Eric", "This is fin-tastic! 🐠", "1h ago"),
                Comment("c58", "Faye", "O-fish-ally my favorite", "2h ago"),
                Comment("c59", "Gary", "Swimming in laughter", "3h ago")
            )
        ),
        Joke(
            id = "18",
            setup = "Why did the computer go to the doctor?",
            punchline = "Because it had a virus!",
            category = "Technology",
            thumbnailColor = Color(0xFF00897B),
            likeCount = 2876,
            comments = listOf(
                Comment("c60", "Helen", "Byte-sized humor 💻", "1h ago"),
                Comment("c61", "Igor", "Ctrl+Alt+Delete this from my memory... never!", "2h ago"),
                Comment("c62", "Jane", "Tech support approved", "3h ago"),
                Comment("c63", "Kyle", "No debugging needed, this joke works!", "4h ago")
            )
        ),
        Joke(
            id = "19",
            setup = "What's orange and sounds like a parrot?",
            punchline = "A carrot!",
            category = "Food",
            thumbnailColor = Color(0xFFFF5722),
            likeCount = 1965,
            comments = listOf(
                Comment("c64", "Lara", "Root-in' for this joke! 🥕", "2h ago"),
                Comment("c65", "Mark", "Sounds about right", "3h ago"),
                Comment("c66", "Nora", "Orange you glad you read this?", "4h ago")
            )
        ),
        Joke(
            id = "20",
            setup = "Why don't programmers like nature?",
            punchline = "It has too many bugs!",
            category = "Technology",
            thumbnailColor = Color(0xFF558B2F),
            likeCount = 4567,
            comments = listOf(
                Comment("c67", "Owen", "console.log('hilarious') 😂", "1h ago"),
                Comment("c68", "Pam", "Debugging reality", "2h ago"),
                Comment("c69", "Rob", "Try-catch this joke if you can", "3h ago"),
                Comment("c70", "Sara", "Exception-ally funny", "4h ago"),
                Comment("c71", "Tom", "Stack overflow with laughter", "5h ago")
            )
        )
    )
}
