//javac Shrinker.java
//java Shrinker -c filename

//package shrinker;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Shrinker {
    static final boolean DEBUG = true;
    static final int MIN_BLOCK_SIZE = 5;
    static int Bl_Length;

    public static void main(String[] args) throws IOException {
        if(args.length != 0){
            if (args[0].equals("-c"))
                Compress(args[1], false);
            else
                if (args[0].equals("-c2"))
                    Compress(args[1], true);
                else
                    if (args[0].equals("-x"))
                        Decompress(args[1]);
                    else 
                        System.out.println("Неизвестный аргумент\n");
        }
        else 
            System.out.println("-c\tСжать файл с декрементом размера блока\n-c2\tСжать файл с делением размера блока\n-x\tИзвлечь файл\n");
    }

    public static void Decompress(String filename) throws IOException {
        pr("Распаковываем файл \"" + filename + "\"\n");
        if(!filename.matches(".*\\.shrinked-\\d+$")) {
            pr("Имя файла не соответствует шаблону\n");
            return;
        }

        Pattern p = Pattern.compile("\\d+$");
        Matcher m = p.matcher(filename);
        m.find();
        int prohod_count=Integer.parseInt(filename.substring(m.start(), m.end()));
        
        byte[] indata = LoadFile(filename);

        pr("Количество проходов: " + prohod_count + "\n");

        byte[] q = new byte[4];

        for (int prohod=0; prohod<prohod_count; prohod++) {
            pr("======================== " + prohod + "\n");
            pr("Размер входных данных: " + indata.length + "\n");

            for (int i=0; i<4; i++)
                q[i] = indata[i];
            int Block_Size=byteArrayToInt(q);
            pr("Длина блока: " + Block_Size + "\n");
            for (int i=0; i<4; i++)
                q[i] = indata[i+4];
            int Block_Count=byteArrayToInt(q);
            pr("Количество повторов: " + Block_Count + "\n");
            int Target_Size = indata.length - 8 - Block_Count*4 + Block_Size*(Block_Count-1);
            pr("Итоговый размер: " + Target_Size + "\n");
            pr("------------------------\n");

            byte[] outdata = new byte[Target_Size];

            int[] Retries = new int[Block_Count];
            for (int i=0; i<Block_Count; i++) {
                for(int j=0; j<4; j++)
                    q[j]=indata[8+i*4+j];
                Retries[i]=byteArrayToInt(q);
            }
            for (int i=0; i<Retries.length; i++)
                pr("Повтор: " + Retries[i] + "\n");   // вывести повторы

            int outdata_i=0;
            int indata_start_re=8+Block_Count*4;
            int indata_start_data=indata_start_re + Block_Size;
            int indata_i=indata_start_data;

            boolean continue_flag;
            while (outdata_i<Target_Size) {
                continue_flag=false;
                for (int j=0; j<Block_Count; j++) { // j - номер повтора
                    if (outdata_i==Retries[j]) {
                        for (int k=0; k<Block_Size; k++) {
                            outdata[outdata_i+k]=indata[indata_start_re + k];
//                            pr("outdata_i+k: " + (int)(outdata_i+k) + "\tindata_start_re + k: " + (int)(indata_start_re + k) + "\t char: " + (char)indata[indata_start_re + k] + "\n");
                        }
                        outdata_i=outdata_i+Block_Size;
                        continue_flag=true;
                        break;
                    }
                }
                if(continue_flag)
                    continue;

                // pr("outdata_i: " + outdata_i + "\tindata_i:            " + indata_i + "\tchar: " + (char)indata[indata_i] + "\n");
                outdata[outdata_i]=indata[indata_i];

                outdata_i++;
                indata_i++;
            }
            indata=outdata;
        }

        if (indata.length > 0) {
            // вывод полученных данных
//            for (int i=0; i<outdata.size(); i++) {
//                if((i%4)==0 & (i<=16)) System.out.println("------------------------------");
//                System.out.println(outdata.get(i) + "\t" + (char)(byte)outdata.get(i));
//            }
            String outfile=filename + ".unshrinked";
            pr("Файл: " + outfile + "\n");
            SaveFile(outfile, indata);
        }
    }

    public static void Compress(String filename, boolean BLOCK_LENGTH_DEVIDE) throws IOException {
        pr("Архивируем файл \"" + filename + "\"\n\n");
        byte[] indata = LoadFile(filename);
        int file_size=indata.length;
        pr("Размер файла:\t" + file_size + "\n");
        Bl_Length=file_size/2;
        int depth=0;
        ArrayList<Byte> outdata = null;
        ArrayList RetriesAL;

        while(true) {
            outdata = new ArrayList<Byte>();
            RetriesAL = FindRetries(indata, BLOCK_LENGTH_DEVIDE);
            if (RetriesAL.isEmpty())
                break;

            byte[] q;   // вспомогательный массив
            q = IntToByteArray((int)RetriesAL.get(0));      // длина блока
            for (int i=0; i<4; i++)
                outdata.add(q[i]);
            q = IntToByteArray(RetriesAL.size()-1);         // количество блоков
            for (int i=0; i<4; i++)
                outdata.add(q[i]);
            q = IntToByteArray((int)RetriesAL.get(1));      // исходный блок
            for (int i=0; i<4; i++)
                outdata.add(q[i]);
            for (int j=2; j<RetriesAL.size(); j++) {
                q = IntToByteArray((int)RetriesAL.get(j));  // повторы
                for (int i=0; i<4; i++)
                    outdata.add(q[i]);
            }
            for (int i=(int)RetriesAL.get(1); i<((int)RetriesAL.get(1)+(int)RetriesAL.get(0)); i++)
                outdata.add(indata[i]); // архивируемый блок
            // данные без повторов
            for (int i=0; i<indata.length; i++) {
                boolean f=false;
                for (int j=1; j<RetriesAL.size(); j++)
                    if (
                            (i>=((int)RetriesAL.get(j))) &
                            (i<((int)RetriesAL.get(j)+(int)RetriesAL.get(0)))
                            )
                        f=true;
                if (!f)
                    outdata.add(indata[i]);
            }

            pr("================================ " + ++depth + "\n");
            pr("Размер исходных данных: " + indata.length + "\n");
            pr("Длина блока: " + RetriesAL.get(0) + "\n");
            pr("Количество повторов: " + (RetriesAL.size()-1) + "\n");
            pr("Начало оригинального блока: " + RetriesAL.get(1) + "\n");
            for (int i=2; i<RetriesAL.size(); i++)
                pr("Начало блока повтора: " + RetriesAL.get(i) + "\n");
            pr("Итоговый размер: " + outdata.size() + "\n");
            pr("Сжатие: " + (int)((float)outdata.size()/(float)indata.length*100) + "%(" +
                    (int)((float)outdata.size()/(float)file_size*100) + "%)\n--------------------------------\n");

            indata = new byte[outdata.size()];
            for (int i=0; i<indata.length; i++)
                indata[i]=outdata.get(i);
        }

        if (indata.length > 0) {
            // вывод полученных данных
//            for (int i=0; i<outdata.size(); i++) {
//                if((i%4)==0 & (i<=16)) System.out.println("------------------------------");
//                System.out.println(outdata.get(i) + "\t" + (char)(byte)outdata.get(i));
//            }
            String outfile=filename + ".shrinked-" + depth;
            pr("\nАрхив: " + outfile + "\n");
            SaveFile(outfile, indata);
        }
    }

    public static ArrayList FindRetries(byte[] indata, boolean BLOCK_LENGTH_DEVIDE) {
        int Input_Length=indata.length;

        ArrayList<Integer> returnarray = new ArrayList<Integer>();
        while (Bl_Length>=MIN_BLOCK_SIZE) {
            pr("Длина блока:\t" + Bl_Length + "\n");
            int max_count=0;
            int max_start=0;
            ArrayList<Integer> max_array = new ArrayList<Integer>();

            for (int Src_Start=0; Src_Start<=Input_Length-Bl_Length*2 ; Src_Start++) {
//                pr("\tНачало исходного блока:\t" + Src_Start + "\n");
                ArrayList<Integer> array = new ArrayList<Integer>();
                for (int Dst_Start=Src_Start+Bl_Length; Dst_Start<(Input_Length-Bl_Length+1); Dst_Start++) {
//                    pr("\t\tНачало тестового блока:\t" + Dst_Start + "\n");

                    boolean f=true;
                    for (int n=0; n<Bl_Length; n++) {
                        if (indata[Src_Start+n] != indata[Dst_Start+n]) {
                            f=false;
                            break;
                        }
                    }
                    if (f) {
                        array.add(Dst_Start);
                        Dst_Start = Dst_Start+Bl_Length-1;
                    }
                } // Dst_Start
//                pr("\tКоличество:\t\t" + array.size() + "\n\t--------------------------------\n");
                if (array.size()>max_count) {
                    max_count=array.size();
                    max_start=Src_Start;
                    max_array=array;
                }
            } // Src_Start

            int predict_size = Input_Length - Bl_Length*(max_array.size()) + 4*(max_array.size()+3);
            if (max_count > 0) {
                pr("\tНайдено блоков: " + max_count + "\n");
                if (Input_Length > predict_size) { // проверка целесообразности
                    pr("Предполагаемый конечный размер: " + predict_size + "\n");
                    returnarray.add(Bl_Length);    // длина блока
                    returnarray.add(max_start);       // адрес исходного блока
                    for (int i=0; i<max_array.size(); i++)
                        returnarray.add(max_array.get(i));   // адреса повторных блоков
                    break;
                }
            }

            if (BLOCK_LENGTH_DEVIDE)
                Bl_Length/=2;
            else
                Bl_Length--;
        } // Block_Length
        return returnarray;
    }

    public static byte[] LoadFile (String Path) throws IOException {
        byte[] x = null;
        try {
            RandomAccessFile f = new RandomAccessFile(Path, "r");
            x = new byte[(int)f.length()];
            f.readFully(x);
            f.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Shrinker.class.getName()).log(Level.SEVERE, null, ex);
        }
        return x;
    }

    public static void SaveFile (String Path, byte[] data) throws IOException {
        try {
            RandomAccessFile f = new RandomAccessFile(Path, "rw");
            for (int i=0; i<data.length; i++) f.write(data[i]);
            f.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Shrinker.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static int byteArrayToInt(byte[] buffer) {
            return (buffer[0] << 24) + ((buffer[1] & 0xFF) << 16) + ((buffer[2] & 0xFF) << 8) + (buffer[3] & 0xFF);
        }

    public static byte[] IntToByteArray(int value) {
        return new byte[] {(byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value };
    }

    public static void pr(String x) { if (DEBUG) System.out.print(x); }
}
