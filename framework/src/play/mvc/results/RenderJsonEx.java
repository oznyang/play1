package play.mvc.results;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.*;
import org.apache.commons.lang.StringUtils;
import play.exceptions.UnexpectedException;
import play.mvc.Http;

/**
 * /**
 * 200 OK with application/json
 * <p/>
 *
 * @author <a href="mailto:oxsean@gmail.com">sean yang</a>
 * @version V1.0, 14-5-22
 */
public class RenderJsonEx extends Result {

    String json;

    public RenderJsonEx(Object o, String dateFormat, SerializeFilter filter, SerializerFeature... features) {
        SerializeWriter out = new SerializeWriter();
        try {
            JSONSerializer serializer = new JSONSerializer(out);
            if (features != null) {
                for (SerializerFeature feature : features) {
                    serializer.config(feature, true);
                }
            }
            serializer.config(SerializerFeature.DisableCircularReferenceDetect, true);

            if (StringUtils.isNotEmpty(dateFormat)) {
                serializer.config(SerializerFeature.WriteDateUseDateFormat, true);
                serializer.setDateFormat(dateFormat);
            }

            if (filter != null) {
                if (filter instanceof PropertyPreFilter) {
                    serializer.getPropertyPreFilters().add((PropertyPreFilter) filter);
                }
                if (filter instanceof NameFilter) {
                    serializer.getNameFilters().add((NameFilter) filter);
                }
                if (filter instanceof ValueFilter) {
                    serializer.getValueFilters().add((ValueFilter) filter);
                }
                if (filter instanceof PropertyFilter) {
                    serializer.getPropertyFilters().add((PropertyFilter) filter);
                }
                if (filter instanceof BeforeFilter) {
                    serializer.getBeforeFilters().add((BeforeFilter) filter);
                }
                if (filter instanceof AfterFilter) {
                    serializer.getAfterFilters().add((AfterFilter) filter);
                }
            }

            serializer.write(o);
            json = out.toString();
        } finally {
            out.close();
        }
    }

    public RenderJsonEx(Object o, SerializerFeature... features) {
        this(o, null, null, features);
    }

    public RenderJsonEx(Object o) {
        this(JSON.toJSONString(o, SerializerFeature.DisableCircularReferenceDetect));
    }

    public RenderJsonEx(String jsonString) {
        json = jsonString;
    }

    @Override
    public void apply(Http.Request request, Http.Response response) {
        try {
            String encoding = getEncoding();
            setContentTypeIfNotSet(response, "application/json; charset=" + encoding);
            response.out.write(json.getBytes(encoding));
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }
}
