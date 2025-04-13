import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.campusmaplogger.databinding.ActivityFullPhotoBinding

class FullPhotoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullPhotoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUri = intent.getStringExtra("imageUri")?.let { Uri.parse(it) }

        imageUri?.let {
            binding.fullImageView.setImageURI(it)
        }

        // Confirm button sends result
        binding.btnConfirm.setOnClickListener {
            val returnIntent = intent
            returnIntent.putExtra("confirmedUri", imageUri.toString())
            setResult(RESULT_OK, returnIntent)
            finish()
        }

        // Cancel button discards
        binding.btnCancel.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}