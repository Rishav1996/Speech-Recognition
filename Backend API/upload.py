import speech_recognition as sr
from flask import Flask,request,render_template
import soundfile as sf
import wave

app=Flask(__name__)

@app.route('/')
def uploadtest():
    return render_template('index.html')

@app.route('/upload',methods=['POST'])
def upload():
    file_loc='./data/temp.pcm'
    write_loc='./data/temp.wav'
    file=request.files['recording']
    file.save(file_loc)
    with open(file_loc, 'rb') as pcmfile:
        pcmdata = pcmfile.read()
    with wave.open(write_loc, 'wb') as wavfile:
        wavfile.setparams((1, 2, 8000, 0, 'NONE', 'NONE'))
        wavfile.writeframes(pcmdata)
    
    try:
        r = sr.Recognizer()
        with sr.WavFile(write_loc) as src:
            audio=r.record(src)
            
        return r.recognize_google(audio)
    except:
        return "Backend Error"
    
if __name__=='__main__':
    app.run(host='0.0.0.0',port=8000,debug=True)


